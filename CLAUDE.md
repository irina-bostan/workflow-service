# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository, plus a "templating playbook" capturing what worked when building it from scratch.

## Project Overview

Corporate travel booking workflow service for TechQuarter. **Java 25 + Spring Boot 3.5** REST API targeting AWS ECS Fargate at 100 rps peak. Four endpoints exposed as REST, backed by PostgreSQL and Redis. Companion React Native (Expo) UI under `ui/`.

## Build & Run Commands

```bash
# Backend
mvn test                                  # unit + slice tests (Surefire, H2 in PG-compat mode)
mvn verify                                # + integration tests + Cucumber BDD (Failsafe, H2)
mvn -Pperf verify -DskipTests -DskipITs   # JMeter perf run (needs running app on local stack)
mvn package -DskipTests                   # fat JAR

# Single test
mvn test -Dtest=BookingServiceTest
mvn test -Dtest=BookingServiceTest#create_validFlightRequest_persistsAndPublishesOutbox

# Local stack — single entrypoint provisions postgres, redis, localstack, cognito-local
bash local/run-local.sh                   # provision + start backend
bash local/run-local.sh --no-start        # provision only

# UI
cd ui && npm install && npm test          # 23 tests across api + screens (jest-expo + RNTL)
cd ui && npm start                        # Expo dev server
```

## Spring Profiles

| Profile | DB | Cache | AWS |
|---|---|---|---|
| `dev` (default) | H2 in-memory | Simple Map | Disabled (NoOp publisher) |
| `local` | PostgreSQL (Docker) | Redis (Docker) | LocalStack SQS / SSM |
| `stage` | RDS `db.t4g.medium` | ElastiCache | Real AWS |
| `prod` | RDS `db.r6g.large` Multi-AZ | ElastiCache | Real AWS |

## Architecture (current state)

Spec-first: `src/main/resources/static/openapi.yaml` is the source of truth. The OpenAPI Generator Maven plugin produces `*ApiDelegate` interfaces and DTOs at build time; we implement the delegates. Each endpoint maps to a `@Transactional` Spring service.

**Booking creation** uses **DB-backed idempotency** (`bookings.idempotency_key` UNIQUE) and a **transactional outbox**: `BookingService` writes the booking row and an `outbox` row in one transaction, then `OutboxRelay` (`@Scheduled`, 500 ms in prod) claims pending rows with `FOR UPDATE SKIP LOCKED LIMIT 100` and publishes to SQS. Safe to run on every ECS task — no double-publish.

**Search** is `@Cacheable` against Redis (5-min TTL); cache miss falls through to `DuffelSearchProvider`.

### Package layout
```
com.aniri.workflow_service/
├── WorkflowServiceApplication.java
├── application/
│   ├── aws/                 # SqsBookingEventPublisher, NoOpBookingEventPublisher, SqsConfig
│   ├── config/              # SecurityConfig, AuditingConfig, etc.
│   ├── duffel/              # DuffelSearchProvider, DuffelConfig
│   ├── health/              # custom liveness / readiness indicators
│   ├── outbox/              # OutboxRelay (@Scheduled)
│   └── properties/          # @ConfigurationProperties classes (CorsProperties, AwsProperties, …)
├── domain/
│   ├── appointment/         # AppointmentService + model/{Entity, Repository, Mapper}
│   ├── booking/             # BookingService + model/{Entity, Repository, Mapper}, exception/
│   ├── employee/            # EmployeeService + model + exception/
│   ├── outbox/              # OutboxEntry, OutboxRepository, OutboxStatus
│   ├── persistence/         # AuditableEntity (@MappedSuperclass)
│   └── search/              # SearchService + model
└── web/
    ├── api/                 # *ApiDelegateImpl (we implement the OpenAPI-generated interfaces)
    ├── error_handling/      # GlobalExceptionHandler → ApiError
    └── filters/             # RequestLoggingFilter
```

### REST Endpoints (no `/api/v1` prefix)
| Method | Path | Notes |
|---|---|---|
| `POST` | `/employees` | Register employee. Unique on `employeeId` and `email`. |
| `POST` | `/bookings` | Requires `Idempotency-Key: <UUID>` header. DB unique on the column dedupes replays. |
| `GET` | `/bookings` | Paginated list by `employeeId`. |
| `GET` | `/bookings/search` | `@Cacheable` against Redis. |
| `POST` | `/bookings/{bookingId}/appointments` | HOTEL bookings only. |

Swagger UI: `http://localhost:8080/swagger-ui.html`

## Key design details

- **Idempotency**: column-based on `bookings.idempotency_key` (UNIQUE). The previous Redis-backed approach was dropped — DB constraint is simpler, transactional, and survives Redis restarts.
- **Transactional outbox**: see `OutboxRelay` + `OutboxRepository.claimPending(N)` (native query with `FOR UPDATE SKIP LOCKED`). Hourly `purgeSent()` deletes SENT > 24h; per-table autovacuum tuned in migration `V5__tune_outbox_autovacuum.sql`.
- **Async logging**: `logback-spring.xml` wraps `CONSOLE_JSON` in an `AsyncAppender` (`neverBlock=true`) so request threads never stall on stdout.
- **Autoscaling**: primary trigger is ALB `RequestCountPerTarget=80`; CPU at 80 % is a safety net only (this app is I/O-bound and CPU never hits 60 % at peak).
- **Entities**: extend `AuditableEntity` (`@MappedSuperclass`) — `createdAt`, `updatedAt`, `@Version`. PKs are `UUID` via `GenerationType.UUID`.
- **MapStruct + Lombok ordering**: in `pom.xml`, the `maven-compiler-plugin` annotation processor paths list Lombok before MapStruct (already configured).
- **Test separation**: Surefire runs `*Test.java`; Failsafe runs `*IT.java` and Cucumber. Cucumber engine auto-discovers `.feature` files; suppressed during `mvn test` so scenarios only run on `mvn verify`.

## Repo structure
```
workflow-service/
├── src/                        # Maven Java source
├── ui/                         # React Native + Expo (TS)
├── infrastructure/             # AWS CDK (TS) — ECS, ALB, RDS, Redis, SQS, alarms
├── docs/
│   ├── design/                 # ARCHITECTURE.md, ER diagram, component + sequence + deployment .puml
│   ├── perf/                   # PERFORMANCE.md (sizing, bottlenecks, autoscaling, SLOs)
│   ├── ci_cd/                  # PIPELINE.md
│   └── alerting/               # ALERTING.md, cloudwatch-alarms.json, cloudwatch-dashboard.json
├── local/                      # docker-compose.yml, run-local.sh, setup-cognito.sh
├── insomnia/                   # Insomnia v4 collection
├── Dockerfile                  # Multi-stage eclipse-temurin:25
├── LICENSE                     # Apache 2.0
├── CONTRIBUTORS.md             # Authors + AI assistance disclosure
└── .github/workflows/ci.yml    # test → build → deploy-staging → deploy-prod
```

---

# Templating playbook — building a similar project from scratch

Distilled from this project. Use these as defaults; deviate when something specific demands it.

## Phase 0: Decide the shape

1. **API-first or code-first?** API-first via OpenAPI + the generator plugin pays off the second the spec stabilises (no controller-DTO drift, generated Insomnia/Postman collections). Don't try to retrofit it later.
2. **One service or microservices?** Default to one. Split only when team boundaries or deploy cadence force the issue.
3. **Sync or async hot path?** Synchronous request/response is the default. Use a transactional outbox the moment you need to publish an event after a DB commit — never `@Async` from inside a `@Transactional` method.

## Phase 1: Skeleton (1–2 hours of AI-assisted work)

- `pom.xml` with Spring Boot parent, Java target, MapStruct + Lombok annotation-processor ordering correct.
- `application.yaml` + per-env profile files (`application-{dev,local,stage,prod}.yaml`). H2 + simple cache for `dev` so the project is `mvn spring-boot:run`-able on a fresh clone.
- `Dockerfile` — multi-stage `eclipse-temurin:NN-jre`, non-root user, `entrypoint` exec form.
- `.github/workflows/ci.yml` — test → build → deploy-staging → manual gate → deploy-prod.
- `LICENSE` + `CONTRIBUTORS.md` from the start. Don't wait until the project is "real."

## Phase 2: Spec + generated layer

- Write the OpenAPI spec FIRST. Pin the generator (`openapi-generator-maven-plugin`) and configure `delegatePattern=true`, `interfaceOnly=false`, `useJakartaEe=true`. Map `UUID` explicitly.
- Generated controllers are not committed; only delegate impls are. `target/generated-sources/openapi/` ends up on the source path.
- Bare-resource responses (no envelope). Errors via `ApiError` with `source / reasonCode / description / details / isRecoverable`.

## Phase 3: Domain

- One package per aggregate root: `domain/<aggregate>/`. Service at the top, `model/{Entity, Repository, Mapper}` underneath, `exception/` alongside.
- All entities extend `AuditableEntity` (`@MappedSuperclass`) with `createdAt`, `updatedAt`, `@Version`.
- PK is `UUID` via `GenerationType.UUID`. DB columns are `TIMESTAMPTZ`, never plain `TIMESTAMP`.
- Idempotency for create endpoints lives on the DB (`UNIQUE` column), not in a side cache.

## Phase 4: Infrastructure-shaped patterns

- **Transactional outbox** for any event publish that follows a DB write. `claimPending()` native query with `FOR UPDATE SKIP LOCKED LIMIT N` so it scales horizontally. Hourly purge of SENT rows + per-table autovacuum tuning so the table doesn't bloat.
- **HikariCP**: `max=20`, `min-idle=5`, `connection-timeout=2000`, `leak-detection=10000`, `pool-name` set so metrics are findable. Connection budget = `tasks × pool` ≤ DB `max_connections` with margin.
- **Async logging**: wrap the JSON encoder in `AsyncAppender` with `neverBlock=true`. Request threads never block on stdout.
- **Autoscaling**: pick the trigger that matches the workload. I/O-bound = ALB `RequestCountPerTarget`. CPU-bound = CPU. CPU at 60–80 % as a *safety net*, never the primary signal for a synchronous-IO service.

## Phase 5: Testing — four layers, in order

1. **Unit + slice** (`*Test.java`, Surefire) — JUnit 5 + Mockito + AssertJ + `@WebMvcTest` for delegates. Cheap, mock everything.
2. **Integration** (`*IT.java`, Failsafe, `@SpringBootTest` + `@DataJpaTest`) — full context, **H2 in PostgreSQL compatibility mode** with `ddl-auto=create-drop` and Flyway disabled in tests. Avoids Docker-specific friction (Colima vs docker-java API version mismatches) and is fast. Real Postgres is what stage testing is for.
3. **BDD** — Cucumber 7 + JUnit Platform engine. Generic HTTP/assertion verbs in `bdd/support/`, per-domain Givens in `bdd/steps/`. `@ScenarioScope` `World` shares state. Reports HTML/JSON/XML to `target/cucumber-reports/`. Suppress the engine during `mvn test` via `-Dcucumber.features=classpath:no-such-features` so scenarios only fire on `mvn verify`.
4. **Performance** — `jmeter-maven-plugin` under a `perf` profile, single JMX with three sequential thread groups (steady → peak → stress), `RequestCountPerTarget` semantics. Run against the live local stack.

## Phase 6: Observability

- **Logs**: structured JSON via `logstash-logback-encoder`, MDC carries `traceId / spanId / correlationId`.
- **Metrics**: enable Micrometer percentile histograms on `http.server.requests` from day one. Without `percentiles-histogram: true` you only get count/sum/max — no SLO tracking. Set `slo:` buckets matching your SLO ladder.
- **Traces**: `micrometer-tracing-bridge-otel` + ADOT collector sidecar in the ECS task; OTLP over loopback.
- **Alarms**: align thresholds to declared SLOs. A `p99 > 2 s` alarm with a 300 ms p99 SLO is alarming for the wrong thing.

## Phase 7: Local stack as one command

- `bash local/run-local.sh` provisions everything (Compose up + Cognito-local pool/client + LocalStack SQS/SSM + writes Cognito Client ID into `ui/.env`). One entry point. Keep modular sub-scripts (`setup-cognito.sh`, etc.) but don't make the user invoke them in order.

## Phase 8: UI

- React Native + Expo with TypeScript. Don't add a UI directory until the API stabilises — UI built against a moving spec is wasted work.
- Test stack: `jest-expo`, `@testing-library/react-native`. Pre-configure `hostComponentNames` in `jest.setup.ts` to skip RNTL's auto-detect probe (it crashes under newer RN + jest-expo combos).
- `babel-preset-expo` *inlines* `process.env.EXPO_PUBLIC_*` at transform time. Set test env vars in `jest.config.js` (runs in main process before workers spawn), not in `jest.setup.ts`.
- Release-build safety: any module that uses bundled credentials should `if (!__DEV__) throw` at module load. Prevents accidentally shipping local-dev USER_PASSWORD_AUTH credentials.

## Pitfalls to avoid

- **`mvn verify` running Testcontainers on Colima**: docker-java's default API version (1.32) is rejected by recent Colima daemons (min 1.44). Bumping testcontainers alone doesn't fix it. Use H2 in PG-compat mode for ITs; reserve real Postgres for stage testing.
- **`@Async` from inside `@Transactional`**: the publish runs on a different thread without the transaction context. Use the outbox.
- **Outbox without `FOR UPDATE SKIP LOCKED`**: every ECS task's relay will read the same PENDING rows and double-publish. Add the lock from day one if `min ≥ 2`.
- **Pool sizing math at p50, not p99**: real working set is `rps × p99_db_time`, not `× p50`. Plus outbox-relay-held connections. Plus burst headroom.
- **Burstable RDS for sustained peak**: `db.t4g.*` exhausts CPU credits at 100 rps held for 5 min. `db.r6g.large` is the floor for prod.
- **`Math.ceil` for "days until X"**: produces "Tomorrow" for 4-hours-from-now. Compare calendar dates with `setHours(0,0,0,0)` then `Math.round`.
- **Stale-closure pagination**: `setBookings([...bookings, ...new])` in a setter captures stale state. Use `setBookings(prev => [...prev, ...new])`.
- **Wrong CloudWatch p99 threshold**: alarm threshold should be 1–2× the SLO, not 6–7×. A `p99 > 2 s` alarm on a 300 ms SLO fires after the SLO has been broken for some time.

## Heuristic: when in doubt

- **Default to deleting the abstraction.** Strategy + Factory was over-engineered for four endpoints. Plain `@Transactional` services + `@RestControllerAdvice` was simpler, more discoverable, and didn't lose anything.
- **Prefer DB constraints to application-level checks** for uniqueness and idempotency.
- **Run perf tests against a live stack with real backing services**, not a mocked one. Local Docker DB is fine for shape; real Multi-AZ commit latency is what the SLO is measured against.
