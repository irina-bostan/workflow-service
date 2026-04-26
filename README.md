# Workflow Service

Corporate travel booking workflow service for TechQuarter. REST API + companion mobile app, deployed on AWS at 100 rps peak.

## What's in this repo

| Directory | What it is |
|---|---|
| [`src/`](src/) | **Backend** — Java 25 / Spring Boot 3.5 REST service. API-first via OpenAPI; transactional outbox for SQS events; HikariCP-tuned PostgreSQL access. |
| [`ui/`](ui/) | **Mobile UI** — React Native + Expo (TypeScript). 5 screens, 6 API clients, jest-expo + RNTL test suite (23 tests). |
| [`infrastructure/`](infrastructure/) | **AWS CDK** (TypeScript). ECS Fargate + ALB + RDS Multi-AZ + ElastiCache + SQS FIFO + CloudWatch alarms. |
| [`local/`](local/) | **One-command local stack**: `bash local/run-local.sh` provisions Docker Compose (PG, Redis, LocalStack, cognito-local), runs the bootstrapping scripts, syncs `ui/.env`, and starts the backend. |
| [`docs/`](docs/) | Design, database schema, CI/CD pipeline, alerting strategy, performance + sizing. |
| [`insomnia/`](insomnia/) | Insomnia v4 collection — every endpoint × 3 environments. |

## Architecture (one paragraph)

API-first: `src/main/resources/static/openapi.yaml` is the source of truth; the OpenAPI Generator Maven plugin produces `*ApiDelegate` interfaces and DTOs at build time. Each endpoint maps to a `@Transactional` Spring service. Booking creation uses **DB-backed idempotency** (`bookings.idempotency_key` UNIQUE) and a **transactional outbox**: the booking row and an outbox row commit together, then `OutboxRelay` (`@Scheduled`, every 500 ms in prod) claims pending rows with `FOR UPDATE SKIP LOCKED LIMIT 100` and publishes to SQS — safe to run on every ECS task. Search is `@Cacheable` against Redis (5-min TTL); cache miss falls through to `DuffelSearchProvider`. Autoscaling is driven primarily by `RequestCountPerTarget = 80` (CPU at 80 % is a safety net, since this app is I/O-bound).

See [`docs/design/ARCHITECTURE.md`](docs/design/ARCHITECTURE.md), the [low-level component diagram](docs/design/component-diagram.puml), the [database ER diagram](docs/design/database-schema.puml), and the [deployment diagram](docs/design/deployment-diagram.puml).

## Running locally — one command

```bash
bash local/run-local.sh             # provisions everything + starts backend
bash local/run-local.sh --no-start  # provision only; you start the app yourself
```

It brings up Docker Compose (PostgreSQL, Redis, LocalStack, cognito-local), provisions the SQS queue + SSM parameters, creates a Cognito user pool / app client / test user, writes the Cognito Client ID into `ui/.env`, and (unless `--no-start`) launches the Spring Boot app under the `local` profile.

To start the UI alongside:

```bash
cd ui && npm install && npm start
```

Tear down: `docker compose -f local/docker-compose.yml down`.

Useful URLs while running:
- Swagger UI: `http://localhost:8080/swagger-ui.html`
- Health check: `http://localhost:8080/actuator/health`
- OpenAPI JSON: `http://localhost:8080/v3/api-docs`

## Backend — build & test

```bash
mvn test                                  # unit + slice tests (Surefire, H2 in PG-compat mode)
mvn verify                                # + integration tests + Cucumber BDD (Failsafe)
mvn -Pperf verify -DskipTests -DskipITs   # JMeter perf run against the live local stack
mvn package                               # fat JAR in target/
docker build -t workflow-service .
```

### Test layers

| Layer | Stack | Location | Runs on | Notes |
|---|---|---|---|---|
| Unit + slice | JUnit 5 + Mockito + AssertJ + `@WebMvcTest` | `src/test/java/**/*Test.java` | `mvn test` (Surefire) | Service-level mocks; `@WebMvcTest` slices for the OpenAPI delegates. |
| Integration | `@SpringBootTest` + `@DataJpaTest` + RestAssured | `src/test/java/**/*IT.java` | `mvn verify` (Failsafe) | Full Spring context, H2 in PostgreSQL compatibility mode (Hibernate-generated schema). |
| BDD scenarios | Cucumber 7 + JUnit Platform + RestAssured | `src/test/resources/features/*.feature` (steps under `bdd/`) | `mvn verify` (Failsafe) | Generic HTTP/assertion verbs in `bdd/support`; per-domain steps in `bdd/steps`; `World` (`@ScenarioScope`) shares state. |
| Performance | JMeter 5.6.3 via `jmeter-maven-plugin` | `src/test/jmeter/workflow-load.jmx` | `mvn -Pperf verify -DskipTests -DskipITs` | Drives a *running* app; not part of the default build. |

`*Test.java` → unit/slice (Surefire); `*IT.java` → integration (Failsafe). Cucumber engine auto-discovers `.feature` files; suppressed during `mvn test` so scenarios only run on `mvn verify`.

### BDD reports

Generated under `target/cucumber-reports/` after every `mvn verify`:
- `cucumber.html` — self-contained HTML dashboard with the full Gherkin and step-by-step results
- `cucumber.json` — machine-readable JSON for downstream tools
- `cucumber.xml` — JUnit-format XML for CI surfaces

Coverage today: **9 scenarios / 42 steps**, all green.

### Performance scenarios

Three thread groups run sequentially against a live local stack:

| Group | Threads | Ramp | Duration | Throughput cap |
|---|---|---|---|---|
| Steady (warm-up) | 10 | 15 s | 60 s | 1 200/min (≈ 20 rps) |
| **Peak** (100 tps requirement reference) | 50 | 30 s | 5 min | 6 000/min (= 100 rps) |
| Stress | 100 | 30 s | 2 min | 12 000/min (≈ 200 rps) |

Override knobs at the CLI: `-Dperf.host=...`, `-Dperf.peak.duration=...`, `-Dperf.peak.throughputPerMin=...`. Outputs land in `target/jmeter/` (HTML dashboard, raw JTL, logs).

For sizing, bottleneck analysis, autoscaling triggers, and SLO targets see [`docs/perf/PERFORMANCE.md`](docs/perf/PERFORMANCE.md).

## REST endpoints

| Method | Path | Description |
|---|---|---|
| `POST` | `/employees` | Register a new employee. |
| `POST` | `/bookings` | Create a booking (requires `Idempotency-Key` header). |
| `GET` | `/bookings` | List bookings by employee (paginated). |
| `GET` | `/bookings/search` | Search flights or hotels (Redis-cached, 5-min TTL; falls through to Duffel on miss). |
| `POST` | `/bookings/{bookingId}/appointments` | Schedule a hotel appointment. |

Full spec: `/v3/api-docs` · UI: `/swagger-ui.html` · Insomnia collection: [`insomnia/`](insomnia/).

## Mobile UI

React Native + Expo (TypeScript). Code lives under [`ui/`](ui/).

```bash
cd ui
npm install
npm start              # Expo dev server (iOS sim / Android emulator / physical device)
npm test               # 23 tests across api/ + screens/ (jest-expo + RNTL)
```

Screens: `Search`, `BookingForm`, `Bookings`, `UpcomingBookings`, `Register` — wired through `@react-navigation/bottom-tabs` + `@react-navigation/native-stack`.

The app talks to the backend via axios with a Bearer-JWT interceptor; the token comes from cognito-local in dev. URLs are environment-configurable: set `EXPO_PUBLIC_API_URL` and `EXPO_PUBLIC_COGNITO_URL` in `ui/.env` for stage/prod builds. Idempotency keys for booking creation use `expo-crypto.randomUUID()`. A startup-time `if (!__DEV__) throw` guard prevents release builds from accidentally shipping the bundled USER_PASSWORD_AUTH credentials — the production auth flow has to be replaced before a release build will run.

## Infrastructure

AWS CDK (TypeScript) in [`infrastructure/`](infrastructure/). Provisions ECS Fargate (with ADOT collector sidecar), ALB, RDS PostgreSQL 16 (`db.r6g.large` Multi-AZ in prod, `db.t4g.medium` non-prod), ElastiCache Redis, SQS FIFO, CloudWatch alarms + dashboard.

```bash
cd infrastructure && npm install
npm run deploy:staging
npm run deploy:prod    # requires manual approval gate
```

CI/CD: [`docs/ci_cd/PIPELINE.md`](docs/ci_cd/PIPELINE.md). Alerting: [`docs/alerting/ALERTING.md`](docs/alerting/ALERTING.md).

---

## Documentation

| Category | Document | What's in it |
|---|---|---|
| Design | [`docs/design/ARCHITECTURE.md`](docs/design/ARCHITECTURE.md) | Architecture overview, key design decisions, component + sequence diagrams. |
| Database | [`docs/design/database-schema.puml`](docs/design/database-schema.puml) | ER diagram. Authoritative DDL is the Flyway migrations in `src/main/resources/db/migration/`. |
| CI/CD | [`docs/ci_cd/PIPELINE.md`](docs/ci_cd/PIPELINE.md) | GitHub Actions pipeline: test → build → deploy-staging → manual gate → deploy-prod. |
| Alerting | [`docs/alerting/ALERTING.md`](docs/alerting/ALERTING.md) | Monitoring strategy + CloudWatch alarms / dashboard JSON exports. |
| Performance | [`docs/perf/PERFORMANCE.md`](docs/perf/PERFORMANCE.md) | Production sizing, bottlenecks, autoscaling, SLO targets, local-vs-prod interpretation. |
| Project guide / templating playbook | [`CLAUDE.md`](CLAUDE.md) | How the repo is organised + a phase-by-phase playbook for templating a similar project from scratch (incl. pitfalls to avoid). |

## License & contributors

[Apache License 2.0](LICENSE) © 2026 Irina Bostan. See [`CONTRIBUTORS.md`](CONTRIBUTORS.md) for authorship details and disclosure of AI assistance.

---

## AI tooling approach

This project was built primarily with **Anthropic Claude Code** (Claude Opus 4.x) as the implementation accelerant, with the author driving architectural and trade-off decisions and reviewing every artifact before commit.

**Where AI was high-leverage**:

- **Spec → scaffolding**: from a one-paragraph architecture prompt, Claude generated `pom.xml`, the OpenAPI spec, MapStruct mappers, Jakarta validation annotations, the per-profile `application-*.yaml` files, and the multi-stage Dockerfile. First-pass correctness on most of it.
- **Test layers — all four**: unit/slice with Mockito + `@WebMvcTest`, `@SpringBootTest` integration tests with H2 in PostgreSQL compatibility mode, Cucumber BDD with shared step-definition helpers, a parameterised JMeter plan with three thread groups. Realistic edge cases (idempotent replay, employee-not-found, non-hotel appointment, stale-closure pagination regression) included by default.
- **Infrastructure as Code**: the AWS CDK stack, CloudWatch alarm JSON, dashboard JSON, GitHub Actions pipeline — high-boilerplate artifacts where review-time is the dominant cost and AI generation collapses authoring time to near zero.
- **Documentation that stays current**: ARCHITECTURE, PERFORMANCE, sequence and component diagrams, ER diagram. AI also catches drift fast — when the WorkflowProcessor pattern was deleted, AI located every stale reference across 8 files.
- **UI development from a Java background**: scaffolded the React Native + Expo app, wired axios with a Bearer-JWT interceptor, set up `jest-expo` + RNTL with the right `host-component-names` workaround, and produced 23 tests including regressions for two real bugs (stale-closure pagination, calendar-vs-timestamp `daysUntil`).

**Where iteration with AI mattered**:

- Performance tuning was a back-and-forth: AI's first pool-sizing math used p50 latency and was too conservative; pushing back on the math (must include p99, outbox-relay-held connections, burst headroom) produced the right answer. Same for the choice between a manual `VACUUM` after DELETE vs per-table autovacuum tuning — the second pass was clearly better than the first.
- RNTL's host-component-names auto-detection probe crashed under RN 0.76 + jest-expo. AI's first fix was wrong; the working fix (`configureInternal({ hostComponentNames: { … } })` in `jest.setup.ts`) only emerged after diagnosing the actual rendered host names.
- `babel-preset-expo` inlines `process.env.EXPO_PUBLIC_*` at transform time; jest-expo's auto-mocks return `undefined` for `expo-crypto.randomUUID()`. Both required hand-written workarounds in `jest.config.js` / `jest.setup.ts`.

**What AI did not do well, and human judgement carried**:

- Architectural simplification — recognising when a Strategy + Factory abstraction was over-engineered for four endpoints and replacing it with plain `@Transactional` services. AI is biased toward elaborate patterns when scope doesn't justify them.
- Trade-off calls under conflicting goals — e.g. retain SENT outbox rows for a 24 h audit window vs delete-on-send for zero bloat. The "correct" answer depends on operational context AI couldn't infer.
- Spotting outdated parts — like a `t4g.medium` RDS reference in a doc that was correct *before* a stage-vs-prod split. AI reliably *applies* changes; noticing they need to be applied is the human's responsibility.

**Documented playbook**: [`CLAUDE.md`](CLAUDE.md) captures phase-by-phase instructions for templating a similar project — what to do in what order, default tech choices that worked, and pitfalls to avoid (Colima/docker-java API mismatch, `@Async` inside `@Transactional`, p50-vs-p99 pool sizing, `EXPO_PUBLIC_*` inlining, etc.). Re-read it before starting the next project.

The discipline that made this work: **AI generates, the engineer reviews for security, correctness, and business intent**. AI is a productivity multiplier on translation tasks (decision → code). The decisions themselves stay with the engineer.
