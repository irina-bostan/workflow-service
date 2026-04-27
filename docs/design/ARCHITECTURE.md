# Architecture Overview

## System Context

The Workflow Service is a stateless Spring Boot 3.5 REST API on Java 25, deployed on AWS ECS Fargate. It sits between a React Native mobile frontend (`ui/`) and the underlying data stores, processing four corporate travel booking workflows at a peak of 100 rps.

```
React Native App (ui/, Expo SDK 52)
       │
  (HTTPS / Bearer JWT)
       │
  AWS WAF + ALB
       │
  ECS Fargate Cluster  ←──── ECR (Docker image, multi-stage temurin:25)
       │  (each task = app container + ADOT collector sidecar)
  ┌────┼─────────────┬──────────────┐
  ▼    ▼             ▼              ▼
 RDS  Redis      SQS FIFO      Cognito (JWKS)
 PG   Cache      Events
```

See `docs/design/component-diagram.puml` for the in-task component view, and `docs/design/deployment-diagram.puml` for the full AWS topology.

## API contract

REST endpoints follow an **API-first** approach: `src/main/resources/static/openapi.yaml` is the source of truth, and the OpenAPI Generator Maven plugin produces controller interfaces and DTOs at build time. We implement the generated `*ApiDelegate` interfaces; the controllers themselves are generated and untouched.

Responses are **bare resources** (no envelope) per OpenAPI. Errors return a structured `ApiError` body with `source / reasonCode / description / details / isRecoverable`. Validation errors flow through `GlobalExceptionHandler` (`@RestControllerAdvice`) and produce the same shape. Authentication is `Authorization: Bearer <JWT>`; tokens are validated against the Cognito JWKS endpoint by Spring Security's OAuth2 resource server.

## Domain layer

Each endpoint maps to one `@Transactional` domain service:

| Endpoint | Service | Notes |
|---|---|---|
| `POST /employees` | `EmployeeService` | Unique-constraint check on `employeeId` and `email`. |
| `POST /bookings` | `BookingService` | Idempotency check on the DB column `idempotency_key`; if present, returns the cached result. Otherwise inserts booking + outbox entry in **the same transaction**. |
| `GET /bookings`, `GET /bookings/{id}`, `GET /bookings/search` | `BookingService`, `SearchService` | List is paginated by `Pageable`; single-fetch is what the UI polls while a booking is PENDING. Search is `@Cacheable` against Redis (5-min TTL); cache miss falls through to `DuffelSearchProvider`. |
| `POST /bookings/{id}/cancel` | `BookingService.cancelByUser` | User-initiated cancel; entity guard allows PENDING|CONFIRMED → CANCELLED, idempotent on already-CANCELLED. **Asymmetric cascade**: cancelling a FLIGHT cascades across every PENDING/CONFIRMED sibling in the trip; cancelling a HOTEL cancels only that booking. Real provider integration would also issue an upstream order-cancel; the mock just flips state. |
| `GET /bookings/{id}/appointments`, `POST /bookings/{id}/appointments` | `AppointmentService` | Booking lookup + insert; HOTEL bookings only on POST. |
| `GET /trips/{id}/bookings` | `BookingService.findByTripId` (delegate: `TripsApiDelegateImpl`) | Returns every booking sharing a `trip_id`. UI uses this to render trip siblings on the detail screen and to size the cancel-cascade dialog. The endpoint lives on `TripsApiDelegateImpl` because the OpenAPI generator buckets by URL prefix. |

Mapping between OpenAPI-generated DTOs and JPA entities is via MapStruct (zero-reflection, compile-time verified). All entities extend `AuditableEntity` (`@MappedSuperclass`) for `createdAt / updatedAt / @Version`. PKs are UUIDs via `GenerationType.UUID`.

### Persistence schema

Authoritative DDL lives in `src/main/resources/db/migration/V*.sql` (Flyway). The ER view: see [`database-schema.puml`](database-schema.puml). Four tables:

- `employees` — registered employees, unique on `employee_id` and `email`.
- `bookings` — FK on `employee_id → employees.employee_id`, unique on `idempotency_key` (the dedup mechanism for `POST /bookings`); `status`, `provider_ref`, `cancellation_reason` track the workflow lifecycle; `trip_id` (indexed) groups bookings travelling together.
- `appointments` — FK on `booking_id → bookings.id` with `ON DELETE CASCADE`.
- `outbox` — pending event log for the transactional outbox. No FK to `bookings` (decoupled by design); `aggregate_id` is a soft reference. Uses a partial index `WHERE status='PENDING'` so the hot-path SELECT stays fast regardless of how many SENT rows are accumulated, and per-table autovacuum tuned to keep the heap bounded at 100 rps (`scale_factor=0.05`, `threshold=1000`).

## Async — transactional outbox

The booking flow can't lose events on SQS publish failure, so the publish is asynchronous through an outbox table:

1. `BookingService.create()` writes the booking row **and** an `outbox` row (`status=PENDING`) in one transaction. The booking is durable before any external call.
2. `OutboxRelay` (`@Scheduled` every 500 ms in prod) claims a batch of `PENDING` rows with `SELECT … FOR UPDATE SKIP LOCKED LIMIT 100`. Concurrent ECS tasks each see a disjoint batch — safe to run on every task.
3. For each row, `SqsBookingEventPublisher` publishes a `BookingCreatedEvent` to `booking-events.fifo` (`messageGroupId = employeeId`, `messageDeduplicationId = bookingId`). On success the row goes to `SENT`; on failure the row stays `PENDING` with an incremented `attempts` counter. After 10 attempts it transitions to `FAILED`.
4. A second `@Scheduled` method (`purgeSent()`, hourly) deletes `SENT` rows older than 24 h, with per-table autovacuum tuned aggressively to keep the heap bounded (~50 MB steady-state at 100 rps).

`docs/perf/PERFORMANCE.md` §2.1–2.3 covers the math behind drain rate, retention, and bloat control.

## Workflow loop — closing the booking lifecycle

The outbox is half the story; the consumer side is what flips a booking from PENDING to its terminal state:

1. `BookingEventConsumer` (`@ConditionalOnProperty(aws.sqs.enabled)`) long-polls the booking queue with `WaitTimeSeconds=20`, max 10 messages per receive. Runs on every ECS task — SQS handles per-message ownership, no coordination needed.
2. Per message: `MockBookingProvider.reserve()` simulates the upstream Duffel order call (95% success, 5% terminal rejection — the mock is honest about being a mock; a real `DuffelBookingProvider` would slot in via the `BookingProvider` interface). On success → `BookingService.confirm(id, providerRef)`; on `BookingProviderRejectionException` → `BookingService.cancel(id, reason)`. Both delete the message after handling.
3. Transient failures (DB unavailable, etc.) leave the message un-deleted. SQS visibility-timeout redelivers; after `maxReceiveCount=3` it lands in the DLQ.
4. A second poller drains the DLQ on a slower cadence and auto-cancels with `reason="Redelivery limit exceeded"`. CloudWatch alarm `BookingDlqAlarm` fires on `ApproximateNumberOfMessagesVisible > 0` so this is operationally visible.
5. Idempotency under SQS at-least-once: `confirm`/`cancel` skip if the booking is no longer `PENDING`; `cancelByUser` skips if already `CANCELLED`. Entity-level guards on `markConfirmed` (PENDING-only) and `markCancelled` (rejects only the double-cancel case) keep the state machine honest.
6. State machine: `PENDING → CONFIRMED` (consumer success), `PENDING → CANCELLED` (consumer rejection or DLQ), `CONFIRMED → CANCELLED` (user-initiated cancel via `POST /bookings/{id}/cancel`). `CANCELLED` is terminal.

The UI mirrors this: `BookingDetailScreen` polls `GET /bookings/{id}` every 2 s while the booking is `PENDING` (30 s safety timeout), renders a 3-step stepper (Submitted → Reserving → Confirmed/Cancelled), and shows a Cancel button when the booking is in `PENDING` or `CONFIRMED`. After the post-create flow, `BookingFormScreen` navigates the user to the `BookingsTab`'s `BookingDetail` so the back button takes them to "My Bookings" rather than the search results.

## Trip grouping

Bookings carry a `trip_id` (Flyway `V7`, default `gen_random_uuid()`) so the system knows which bookings travel together. A request can either (a) omit `tripId` and get a fresh trip-of-one, or (b) supply an existing `tripId` from a prior booking response to link the new booking into that trip — `BookingService.create` validates same-employee ownership before accepting a supplied tripId.

`BookingService.cancelByUser` is **asymmetric** by resource type to match real-world product semantics:

- **FLIGHT cancel** — looks up siblings via `findByTripIdOrderByCreatedAtAsc` and cancels every PENDING/CONFIRMED booking in the trip in the same transaction. The flight is the trip's anchor; without it the rest of the trip can't happen.
- **HOTEL (or any non-flight) cancel** — cancels only the requested booking. The repository's sibling lookup isn't even called. Use case: user changes mind on the hotel but keeps the flight.

The cancel response always returns the originating booking (single `Booking`); the UI fetches `GET /trips/{tripId}/bookings` separately to reflect siblings. The detail screen's Cancel button label flips to **"Cancel trip (N bookings)"** only when the focused booking is a FLIGHT with multi-booking trip; otherwise it stays at "Cancel booking".

The bookings list (`BookingsScreen`) groups by `tripId` and buckets trips into:

- **upcoming** — at least one booking is PENDING/CONFIRMED with a future departure. Sorted by earliest departure ASC (nearest first).
- **past** — all bookings have past departures and at least one is not CANCELLED.
- **cancelled** — every booking in the trip is CANCELLED. Collapsed under its own accordion.

A trip is never split across buckets — a flight + hotel always stay together in the same section. The `BookingForm` auto-detects the user's most recent open trip after they enter their employee ID and offers a toggle to link the new booking into it; the toggle is what's normally used to bind a flight + hotel into one cascade-cancellable trip from the UI.

## High-throughput design (100 rps peak)

| Concern | Mechanism |
|---|---|
| Stateless service | No server-side session; all state in DB + Redis |
| Horizontal scaling | ECS autoscaling, min 2 / max 10. Primary trigger: ALB `RequestCountPerTarget = 80`. CPU 80 % as a safety net. |
| DB connections | HikariCP `max=20`, `min-idle=5`, 2-second connect timeout, 10-second leak detection |
| DB sizing | `db.r6g.large` Multi-AZ in prod (`max_connections ≈ 1 500`); `db.t4g.medium` non-prod |
| Search latency | Redis `@Cacheable` with 5-min TTL |
| Booking deduplication | `Idempotency-Key: <UUID>` header → unique DB constraint on `bookings.idempotency_key`; replays return the prior result |
| Reliable side-effects | Transactional outbox + relay (above) — booking commits before publish |
| Logging on hot path | Logback `AsyncAppender` wraps the JSON encoder; `neverBlock=true` so request threads never stall on stdout |
| JVM warm-up | ECS `min=2` always hot; readiness probe holds the LB until JIT warm |

## Spring profiles

| Profile | DB | Cache | AWS |
|---|---|---|---|
| `dev` (default) | H2 in-memory | Simple Map | Disabled (NoOp publisher) |
| `local` | PostgreSQL (Docker) | Redis (Docker) | LocalStack SQS / SSM |
| `stage` | RDS `db.t4g.medium` | ElastiCache | Real AWS |
| `prod` | RDS `db.r6g.large` Multi-AZ | ElastiCache | Real AWS |

See `local/run-local.sh` for the Compose stack used by `local`.

## Key design patterns

| Pattern | Where | Benefit |
|---|---|---|
| API-first / generated controllers | OpenAPI Generator Maven plugin | Spec is authoritative; drift is impossible |
| Delegate pattern | `*ApiDelegateImpl` | Implementation is decoupled from generated controllers |
| Transactional outbox | `OutboxEntry` + `OutboxRelay` | No dual-write window between DB commit and SQS publish |
| Repository | Spring Data JPA interfaces | Swappable persistence; easy to test |
| Template Method | `AuditableEntity` `@MappedSuperclass` | DRY auditing fields across entities |
| Adapter | MapStruct mappers, `DuffelSearchProvider` | Zero-reflection conversion; external API isolated behind a port |

## Observability

- **Logs**: structured JSON via `logback-spring.xml` (`AsyncAppender` wrapper for the JSON encoder). MDC carries `traceId / spanId / correlationId / observation` so log lines join with traces and can be filtered to one workflow span (`grep '"observation":"booking.create"'`).
- **Metrics**: Micrometer with `percentiles-histogram` enabled on `http.server.requests` for per-route p50/p95/p99 + SLO-bucket counters at 50/100/150/300/500/1000 ms. Exported via OTLP to the ADOT sidecar → CloudWatch.
- **Traces**: OpenTelemetry via `micrometer-tracing-bridge-otel` (BOM pinned to `1.60.1` in `pom.xml` to clear the `Unsafe.objectFieldOffset` warning Java 25 emitted from the Spring-Boot-pinned `1.49.0`), sampled at 10 % in prod, 100 % elsewhere. Workflow-critical methods carry `@Observed` (`booking.create`, `booking.confirm`, `booking.cancel`, `booking.cancel.user`, `outbox.publish`, `booking.consumer.poll`, `booking.consumer.poll.dlq`, `booking.provider.reserve`, `appointment.create`) so the trace tree shows the business operation, not just the HTTP layer. `ObservabilityConfig` registers an `ObservationHandler` that pushes each observation's name into MDC for log correlation.
- **Trace propagation across SQS** is not yet wired — the outbox→consumer hop produces two separate traces. Adding the OTel AWS SDK auto-instrumentation (or manual `traceparent` injection into SQS message attributes) would link them; tracked as future work.
- **SLOs and alerts**: `docs/perf/PERFORMANCE.md` §4 defines per-endpoint SLO targets; `docs/alerting/cloudwatch-alarms.json` holds the matching thresholds.
