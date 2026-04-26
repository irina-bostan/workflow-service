# Local development

Start the full local stack (Postgres on host, Redis + LocalStack + Jaeger + cognito-local in Docker, then the Spring app).

## One-time setup

1. **PostgreSQL on the host** (not in Docker — `application-local.yaml` points at `localhost:5432`):
   ```bash
   bash local/setup-local-postgres.sh
   ```
   Creates role `workflow` (password `workflow`) and databases `workflowdb` + `workflowdb_shadow`.

2. **Cognito user pool, app client, and test user** (run after the stack is up — see step 1 of "Daily run"):
   ```bash
   bash local/setup-cognito.sh
   ```
   The script prints the generated **Pool ID**, **Client ID**, and the **issuer URI** to use. Paste those into:
   - `src/main/resources/application-local.yaml` → `spring.security.oauth2.resourceserver.jwt.issuer-uri` and `jwk-set-uri`
   - Insomnia env var `cognitoClientId`

## Daily run

```bash
bash local/run-local.sh
```

That script:
1. `docker compose up -d` — starts Redis, LocalStack, Jaeger, cognito-local
2. Waits for Postgres / Redis / LocalStack to be healthy
3. Provisions LocalStack SQS queue + SSM params
4. `mvn spring-boot:run -Dspring-boot.run.profiles=local`

App will be on `http://localhost:8080`.

## Useful endpoints

| What | URL |
|---|---|
| Service | http://localhost:8080 |
| Swagger UI | http://localhost:8080/swagger-ui.html |
| Health (full, with details) | http://localhost:8080/actuator/health |
| Liveness probe | http://localhost:8080/actuator/health/liveness |
| Readiness probe | http://localhost:8080/actuator/health/readiness |
| Prometheus scrape | http://localhost:8080/actuator/prometheus |
| Jaeger UI (traces) | http://localhost:16686 |
| cognito-local | http://localhost:9229 |
| LocalStack | http://localhost:4566 |
| H2 console (dev profile only) | http://localhost:8080/h2-console |

## Health & observability

Three health endpoints, each showing a different slice:

| Endpoint | Components included | Used by |
|---|---|---|
| `/actuator/health` | everything: `db`, `redis`, `cognito`, `sqs`, `diskSpace`, `ping` | manual debugging |
| `/actuator/health/liveness` | `livenessState` only — JVM is alive | ECS task health check (restarts container on failure) |
| `/actuator/health/readiness` | `readinessState` + `db` + `redis` | ALB target health check (pulls pod from rotation on failure) |

Cognito and SQS are intentionally **not** in `readiness` — if either is down, every pod is equally affected; pulling them all from the LB just takes the API offline. Those components show up in `/actuator/health` for ops visibility but don't gate traffic.

Each indicator carries details (response time, queue depth, status code, etc.). Sample:

```bash
curl -s http://localhost:8080/actuator/health | jq
```
```json
{
  "status": "UP",
  "components": {
    "cognito": {
      "status": "UP",
      "details": {
        "jwk-set-uri": "http://localhost:9229/local_70VnjvDS/.well-known/jwks.json",
        "status-code": 200,
        "response-time-ms": 42
      }
    },
    "sqs": {
      "status": "UP",
      "details": {
        "queue-url": "http://sqs.eu-west-1.localhost.localstack.cloud:4566/000000000000/booking-events.fifo",
        "approximate-messages": "0",
        "response-time-ms": 28
      }
    },
    "db":    { "status": "UP", "details": { "database": "PostgreSQL", "validationQuery": "isValid()" } },
    "redis": { "status": "UP", "details": { "version": "7.2.0" } }
  }
}
```

**Quick checks for the observability stack:**

```bash
# liveness (returns 200 / {"status":"UP"} when the JVM is alive)
curl -i http://localhost:8080/actuator/health/liveness

# readiness (200 only when DB + Redis are reachable from this pod)
curl -i http://localhost:8080/actuator/health/readiness

# Prometheus metrics — find http_server_requests_seconds, jvm_*, hikaricp_*, etc.
curl -s http://localhost:8080/actuator/prometheus | grep http_server_requests_seconds_count

# Traces — Jaeger UI (browser)
open http://localhost:16686
```

In Jaeger, hit any endpoint first (e.g. `GET /bookings?employeeId=...`), then in the UI select service `workflow-service` → Find Traces. Click a trace to see the span tree (HTTP server → JDBC → Duffel RestClient if applicable). The `traceId` in your application logs matches the trace ID in Jaeger — paste it into Jaeger's "Lookup by Trace ID" to jump straight to it.

**Same paths in stage/prod**, behind the ALB:
- ECS task health check pings `…/actuator/health/liveness` directly inside the task.
- ALB target group pings `…/actuator/health/readiness` from the load balancer.
- Full `/actuator/health` is reachable through the ALB but `show-details` should be tightened (`when-authorized`) before exposing publicly.

## Auth flow (Insomnia)

1. Run **Auth → Get ID Token** — response script writes the JWT to env var `token`.
2. Run any protected request — `Authorization: Bearer {{ _.token }}` is set on the request's auth tab.

If you get 401 after a fresh `setup-cognito.sh` run, double-check:
- `application-local.yaml` `issuer-uri` matches the **pool ID** the script printed (e.g. `local_70VnjvDS`), not the pool name.
- `local/.cognito/config.json` has `TokenConfig.IssuerDomain: "http://localhost:9229"` so tokens are minted with `iss: http://localhost:9229/<pool-id>` (cognito-local otherwise uses `0.0.0.0`).
- Insomnia env var `cognitoClientId` matches the `Client ID` printed by the setup script.

## Stop everything

```bash
docker compose -f local/docker-compose.yml down
# Ctrl-C in the terminal running mvn spring-boot:run
```

Add `-v` to `down` to wipe Postgres/Redis/LocalStack/cognito-local data.

## iOS simulator

Expo app (the one we used yesterday) — runs Metro and opens the iOS simulator with the app loaded:

```bash
npx expo start --ios          # from the Expo project root
# or, if expo-cli is installed globally:
expo start --ios
# shorthand once Metro is running: press `i` in the terminal
```

Bare simulator without Expo:

```bash
open -a Simulator                       # launch the Simulator app
xcrun simctl list devices available     # see installed device runtimes
xcrun simctl boot "iPhone 15"           # boot a specific device by name
xcrun simctl shutdown all               # shut everything down
xcrun simctl erase all                  # wipe all simulator data
```

The simulator hits the host's `localhost` directly, so the iOS app can call `http://localhost:8080` against the running workflow-service without any port-forwarding.

## Profiles

- `dev` — H2 in-memory, no Docker, security disabled (`security.jwt.enabled: false`). For unit tests and quick local iteration without infra.
- `local` — full stack (this README).
- `stage`, `prod` — env-var driven, real Cognito issuer.

Switch via `-Dspring-boot.run.profiles=<name>` or set `SPRING_PROFILES_ACTIVE`.

### Inspecting structured JSON logs locally

Default output is JSON (matches stage/prod). `local` and `dev` profiles override `logging.json-structure.enabled: false` so the console shows human-readable text.

Flip to JSON for one run via env var:

```bash
LOGGING_JSON_STRUCTURE_ENABLED=true mvn spring-boot:run
```

Or override on the command line:

```bash
mvn spring-boot:run -Dspring-boot.run.arguments=--logging.json-structure.enabled=true
```

Each log line is then a single JSON object: `@timestamp`, `level`, `logger_name`, `thread_name`, `message`, `mdc.{traceId,spanId,correlationId}`, plus `app` / `profile` from the encoder. Pipe through `jq` for readability:

```bash
LOGGING_JSON_STRUCTURE_ENABLED=true mvn -q spring-boot:run \
  | jq -c 'select(.level) | {ts:.["@timestamp"], lvl:.level, msg:.message, trace:.mdc.traceId}'
```

Stack traces become a single `stack_trace` field. Same shape CloudWatch Logs Insights receives in stage/prod.
