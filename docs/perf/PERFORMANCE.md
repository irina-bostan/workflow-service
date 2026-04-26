# Performance: production sizing & scaling

Target: **100 rps peak**, p95 < 150 ms, p99 < 300 ms for the booking-creation hot path. Headroom to ~200 rps for unexpected spikes (the JMeter `stress` group exercises this). HA across 2 AZs is non-negotiable.

This doc is organised by tier, then by concern. Code changes that follow from the analysis are applied to `src/main/resources/application-prod.yaml` and `infrastructure/lib/workflow-service-stack.ts`.

---

## 1. Right-sizing each tier

### 1.1 Compute — ECS Fargate

**Current**: 2 vCPU / 4 GB per task, `desiredCount=2`, `min=2`, `max=10`, CPU-target autoscaling at 60 %.

**Analysis at 100 rps**:
- In-flight requests at p50 ≈ 50 ms → ~5 concurrent → trivial for any task size. Compute is **not** the bottleneck.
- Spring Boot 3.5 + Tomcat default 200 worker threads → single task can comfortably handle 100 rps with low CPU.
- ADOT collector sidecar shares the task; budget ~0.2 vCPU + 100 MB for it.
- JIT warm-up: first ~100 requests after a fresh task come up are slower. With `min=2` and 5-min scale-out cooldown, warm-up cost is bounded to scale events.

**Verdict**: 2 vCPU is appropriate (gives JIT/GC headroom + sidecar). 4 GB is generous — JVM heap settles around 1.2–1.5 GB; could trim to 2 GB to save cost, but the marginal saving is small (~$15/mo per task) and the safety against a memory spike + OOMKilled is worth keeping.

**Recommendation**: keep current sizing. Re-evaluate `max=10` if the autoscaling trigger changes (see Dimension 3).

### 1.2 Database — RDS PostgreSQL + HikariCP

**Before**: `db.t4g.medium` (2 vCPU, 4 GB RAM, **burstable**), Multi-AZ in prod. HikariCP per-task: `max=20`, `min-idle=5`, `connection-timeout=30 s`, `leak-detection=60 s`.

**Analysis — instance class**:
- `db.t4g.medium` is a *burstable* instance. CPU credits accumulate when CPU < baseline (20 %) and burn during sustained load. At 100 rps held for 5 min (the peak scenario), CPU credit depletion is a real risk — when credits run out, CPU throttles to 20 % and latency degrades sharply.
- For a **production** workload at sustained peak we want non-burstable + more cache. `db.r6g.large` (2 vCPU, 16 GB) gives stable performance, 4× the memory for shared buffers / plan cache, and lifts `max_connections` from ~75 to ~1 500. Cost delta vs t4g.medium Multi-AZ in eu-west-1: ~$226/mo (see cost table below); justified for prod stability.
- Multi-AZ already enabled in prod ✓ (sync replication adds ~1–2 ms to writes — acceptable).
- Stage / non-prod stays on `db.t4g.medium` — bursty traffic, lower cost.

**Analysis — connection pool**:

Per-request DB time for booking creation: employee lookup + idempotency check + insert booking + insert outbox + commit (Multi-AZ sync) ≈ **20 ms at p50, ~100 ms at p99**.

Concurrent connections needed per task at peak (50 rps/task with 2 tasks):
- p50: 50 × 0.020 = **1** concurrent
- p99: 50 × 0.100 = **5** concurrent
- \+ outbox relay holding a connection during `SELECT … FOR UPDATE SKIP LOCKED`
- \+ background work (health checks, scheduled tasks)
- \+ burst headroom for traffic spikes during a scale-out lag

Realistic working set: **15–20 conn/task**. Keeping `max=20` is the right ballpark; smaller would underflow during p99 bursts.

**Pool × pod math** (with ECS `min=2`, `max=10`):

| Tasks | Pool/task | Total conns | vs t4g.medium (~75) | vs r6g.large (~1 500) | ECS Fargate cost/mo (eu-west-1) |
|---|---|---|---|---|---|
| 2 (baseline) | 20 | 40 | OK | OK | ~$144 |
| 5 (mid scale) | 20 | 100 | **breaks** | OK | ~$360 |
| 10 (peak burst) | 20 | 200 | **breaks** | OK | ~$720 |

ECS cost = 2 vCPU × $0.04048/hr + 4 GB × $0.004445/GB-hr ≈ $0.099/hr/task ≈ **$72/mo per task** (730 hrs/mo). Peak-burst rows are upper bounds — real monthly cost depends on how long the autoscaler holds those tasks. The `r6g.large` upgrade is what makes safe scale-out possible: without it, the second the autoscaler adds a 4th task, RDS starts refusing connections.

**Code changes applied**:
- `application-prod.yaml`:
  - `pool-name: workflow-pool` — surfaces clearly in Micrometer (`hikaricp_*` metrics) and CloudWatch.
  - `maximum-pool-size: 20` — kept (the working-set math above).
  - `minimum-idle: 5` — keep warm connections during quiet periods so the first peak request doesn't pay connection-establish latency.
  - `connection-timeout: 2000` (was 30 000) — **fail fast**. At 100 rps, a 30 s timeout means up to 3 000 requests pile up behind an exhausted pool before we know we have a problem; 2 s surfaces the issue immediately to the caller and to the alerting layer.
  - `leak-detection-threshold: 10000` (was 60 000) — Hikari logs a warning if a connection is held > 10 s, so a missing `close()` or a runaway query is flagged at 10 s instead of 60 s. Booking-flow connections normally release in < 100 ms, so 10 s is conservative but well above any legitimate operation.
- `infrastructure/lib/workflow-service-stack.ts`:
  - RDS `instanceType` now toggles on `isProd`: `r6g.large` in prod, `t4g.medium` elsewhere.

**Cost impact of the §1.2 changes** (eu-west-1, on-demand, per environment):

| Change | Before | After | Monthly cost | Delta |
|---|---|---|---|---|
| RDS prod (Multi-AZ) | db.t4g.medium ≈ $107/mo | db.r6g.large ≈ $333/mo | $333 | **+$226** |
| RDS stage (single-AZ) | db.t4g.medium ≈ $53/mo | unchanged | $53 | $0 |
| HikariCP tuning | n/a | n/a | n/a | $0 (config-only) |

Pricing is approximate (AWS list prices, no Reserved Instances or Savings Plans). Storage (gp3 ~$0.115/GB-mo) and data transfer are minor at this footprint and excluded.

### 1.3 Cache — ElastiCache Redis

*(deferred)*

### 1.4 Queue — SQS FIFO

*(deferred)*

---

## 2. Likely bottlenecks at peak

For each candidate: *does it actually hurt at sustained 100 rps?* If yes → addressed in code. If no → flagged with the threshold at which it starts to hurt, so we know what to monitor.

### 2.1 Outbox table bloat — **does hurt over time**

At 100 rps the `outbox` table sees:

| Per second | Per hour | Per day |
|---|---|---|
| 100 INSERTs (PENDING) | 360 k | 8.6 M |
| 100 UPDATEs (PENDING → SENT) | 360 k | 8.6 M |
| 100 DELETEs (cleanup of old SENT) | 360 k | 8.6 M |

PostgreSQL UPDATE is implemented as INSERT-new-tuple + mark-old-as-dead under MVCC, and DELETE just marks dead. Without bounding, the heap grows without limit, autovacuum I/O competes with the relay's own queries, and snapshot/restore/scan times degrade linearly with table size. The hot path itself stays fast — the partial index `idx_outbox_pending WHERE status='PENDING'` only ever holds the live backlog (~50 entries at peak) — but everything around the hot path suffers.

**Mechanism in place**:

1. **24-h retention via `OutboxRelay.purgeSent()`** — runs hourly, deletes SENT rows older than 24 h. Idempotent across ECS tasks (concurrent DELETEs of already-deleted rows are a no-op under MVCC; lock contention is negligible at 360 k rows/hr). The 24 h window is cheap insurance: it lets ops introspect the last day's events without going to CloudWatch, and distinguishes a successfully-published row from a phantom delete.
2. **Per-table autovacuum tuning** (`V5__tune_outbox_autovacuum.sql`) — `autovacuum_vacuum_scale_factor=0.05`, `threshold=1000`. Default `scale_factor=0.2` would let dead tuples reach 20 % of the table (~1.7 M) before vacuuming, which on this workload means ~2.4 h between cycles and bloat north of 300 MB. Tightened settings cycle every ~30–40 min, capping unreclaimed heap at ~50 MB.

**Why not "DELETE on send" instead** (skip the SENT state entirely): tempting — zero bloat, simplest code — but it sacrifices the audit window and the ability to distinguish a row that's failed N times from one that was never published. Cheap to keep, expensive to reconstruct from CloudWatch. Reconsider only if compliance pushes retention requirements one way or the other.

**Why not VACUUM FULL or pg_repack**: at the volumes above, autovacuum keeps up. VACUUM FULL takes an exclusive lock; pg_repack needs an extension. Not worth it unless monitoring shows autovacuum can't drain.

### 2.2 Synchronous logging on the request path — **does hurt** at peak

At 100 rps with ~5 log lines per booking flow, each task emits ~500 lines/sec to stdout. The default Logback `ConsoleAppender` writes synchronously on the *request* thread; under contention (CloudWatch agent slow to drain, stdout buffer full) request latency spikes directly with logging stalls.

**Mechanism in place**: `logback-spring.xml` wraps `CONSOLE_JSON` in an `AsyncAppender` with `queueSize=2048` (4 s of buffer at peak), `neverBlock=true` (drop log events rather than stall the app thread when the queue fills), and `includeCallerData=false` (skip the expensive stack walk for the caller class). Trade: lose visibility into the worst spike, keep request latency stable. Acceptable for a high-RPS service.

The text appender (dev/local only) stays synchronous — at single-threaded local load it doesn't matter and async would just complicate log-tailing.

### 2.3 Outbox drain rate — **headroom check, not a current issue**

Drain rate depends on claim batch size (`OutboxRelay.CLAIM_BATCH_SIZE = 100`) and poll cadence (`outbox.relay.fixed-delay-ms`, **500 ms** in prod, 2000 ms elsewhere).

| Tasks | Per-task drain | Total drain | vs 100 rps inflow |
|---|---|---|---|
| 2 (baseline) | 200/sec | **400/sec** | 4× headroom |
| 10 (peak burst) | 200/sec | 2 000/sec | 20× |

Per cycle: 100 events × ~10 ms SQS round-trip ≈ 1 s of held DB connection. With 2 baseline tasks that's ~2 connections continuously held, well inside the 40-conn baseline Hikari budget (§1.2). **No issue at 100 rps.**

If sustained inflow ever exceeded ~400 rps with 2 tasks, drain would fall behind and the table would grow. The fix at that point is the next item.

### 2.4 SQS per-message round-trip — **headroom limit**, not a 100 rps issue

The relay does one `sendMessage` per event (~10 ms p50 in-region). At 100 rps inflow this is fine (§2.3). At ~400 rps inflow per task it caps drain.

`sendMessageBatch` (up to 10 messages per call, FIFO-aware) would cut held-connection time ~10× and lift per-task drain capacity to ~2 000/sec. Not applied yet — small refactor of `SqsBookingEventPublisher` to batch by `messageGroupId`. Defer until measured: if monitoring shows `outbox` queue depth growing, this is the lever.

### 2.5 Cold task startup — **edge case during scale events**, not sustained peak

Spring Boot 3.5 + JIT warmup: a fresh task takes 5–10 s to start and ~100–200 requests before JIT-compiled paths stabilise. During scale-out, the new task is in service but slower for ~30 s. At sustained 100 rps with `min=2` the autoscaler doesn't fire (§3 will revisit triggers), so this only matters during AZ failure, deployment, or burst scale-out.

Mitigations already in place: `min=2` (no cold path for first request of the day), readiness probe on `/actuator/health/readiness` (LB doesn't route until app is up), `start-period=60s` on the container health check (no premature kill during JIT warmup).

If p99 visibly degrades during scale events, options: synthetic warm-up request at startup, or `-Dspring.aot.enabled=true` for 30–50 % faster startup. **Not applied — measure first.**

### 2.6 GC pauses — **tail latency only**

JVM 25 with G1GC on a 4 GB container, default flags. Typical pause: 5–20 ms; occasional Mixed GC at 50–100 ms. Doesn't affect throughput at 100 rps, only p99. ZGC would shave the tail (sub-ms pauses) at a small throughput tax — overkill at this load. **Recommendation**: leave G1GC, add `-XX:MaxGCPauseMillis=100` to the container command only if p99 starts breaching SLO (§4 will define those thresholds).

---

## 3. Autoscaling triggers

The booking flow is I/O-bound: each request spends ~5–10 ms of CPU and ~30–50 ms in DB/Redis/network wait. CPU is therefore a *lagging* indicator on this workload — it doesn't move much until something else (HikariCP, autovacuum, GC) is already in trouble.

### 3.1 Why CPU as a primary trigger doesn't work here

CPU per task at sustained throughput on a 2-vCPU task:

| Inflow | Per-task RPS | CPU per task |
|---|---|---|
| 100 rps (peak) | 50 | ~25 % |
| 200 rps (stress) | 100 | ~50 % |
| 240 rps | 120 | ~60 % ← old trigger fires |

A 60 % CPU target meant the autoscaler didn't see saturation until **2.4× peak load** — well past the point where p95 latency would already have degraded for unrelated reasons (connection pool pressure, GC churn, autovacuum I/O). Scale-out arrives too late to prevent SLO breach.

### 3.2 Primary trigger: `RequestCountPerTarget` at 80/task

Direct, predictable, matches what we actually want to manage (load per task). Target tracking on `RequestCountPerTarget=80`:

| Total inflow | Optimal task count | Per-task rps | Behavior |
|---|---|---|---|
| 100 rps (peak) | 2 (baseline) | 50 | Below target → **stays at 2** |
| 160 rps | 2 | 80 | At target — no change |
| 200 rps (stress) | 3 | 67 | +1 task |
| 500 rps | 7 | 71 | Steady scale-out |
| 800 rps | 10 (max) | 80 | At max capacity |

**Headroom math**: 80/task is ~60 % above the per-task share at peak (50). That gap absorbs short bursts without scaling, but anything sustained above ~150 rps total triggers scale-out before per-task latency degrades. Per-task safe capacity from §2.3 analysis is ~200 rps, so target=80 leaves a 2.5× cushion between trigger and saturation.

### 3.3 Secondary trigger: CPU at 80 % (safety net)

Kept as a backup, raised from 60 % → 80 %. This won't fire under normal traffic — at 200 rps stress, CPU is ~50 %. It only triggers if something CPU-bound regresses (serialization bug, log flood, infinite loop), in which case scale-out is a temporary buffer until the regression is rolled back. ECS auto-scaling takes the **max** of both policies' recommendations.

### 3.4 Cooldowns (unchanged)

| Direction | Cooldown | Reason |
|---|---|---|
| Scale-out | 60 s | Spring Boot startup ~30 s + readiness probe ~30 s = task healthy in ~60 s. Don't trigger faster than capacity comes online. |
| Scale-in | 300 s | Avoid flapping when load drops; absorbs short troughs without yo-yoing tasks. |

### 3.5 Cost impact

Normal operation (≤ 100 rps peak): autoscaler stays at baseline 2 tasks → **no change** to the ~$144/mo ECS bill.

Sustained traffic above 100 rps adds tasks at +$72/mo per task (per §1.2). Worst-case at max capacity: 10 tasks × $72 = ~$720/mo. The trade is responsive scaling for higher peak-period bills — usually the right trade since latency SLA breaches cost more than ECS hours.

### 3.6 Deferred

- **p95 latency-based scaling** as a leading indicator. Needs *step-scaling* policy (different API from target tracking) and a custom CloudWatch metric. Defer until monitoring shows latency spikes that load alone doesn't predict.
- **Predictive scaling** (AWS service). Needs ~14 days of metric history. Worth turning on once prod traffic patterns are established.

### 3.7 Code changes applied

- `infrastructure/lib/workflow-service-stack.ts`:
  - Captured `targetGroup` from `listener.addTargets(...)` so the new policy can reference it.
  - Added `scaling.scaleOnRequestCount('RequestCountScaling', { targetGroup, requestsPerTarget: 80, … })` as the primary trigger.
  - Raised the existing CPU target from 60 % → 80 % (now a safety net, not the primary signal).
  - Both policies share `scaleInCooldown=300s` / `scaleOutCooldown=60s`.

---

## 4. Latency SLOs

What we promise the caller, and how every threshold elsewhere in this doc was chosen.

### 4.1 Per-endpoint targets

Estimates are for production (Multi-AZ RDS in eu-west-1, Redis primary + replica, ECS Fargate task). Numbers come from per-component timing rolled up: ALB hop (~1 ms), JWT validation (~1 ms cached), Redis GET (~1 ms), DB SELECT/INSERT (~5–15 ms each), Multi-AZ commit (~2–5 ms), GC variance (~5–50 ms at p99).

| Endpoint | p50 | p95 | p99 | Error rate | Notes |
|---|---|---|---|---|---|
| `POST /bookings` | 50 ms | **150 ms** | 300 ms | < 0.1 % | The 100 rps hot path. Idempotency check + employee lookup + 2 inserts + Multi-AZ commit. |
| `POST /employees` | 30 ms | 100 ms | 200 ms | < 0.1 % | Single INSERT with unique-constraint check. |
| `POST /bookings/{id}/appointments` | 30 ms | 100 ms | 200 ms | < 0.1 % | Booking lookup + INSERT + commit. No outbox write. |
| `GET /bookings` (paginated) | 30 ms | 80 ms | 150 ms | < 0.1 % | Indexed lookup, no writes. |
| `GET /bookings/search` *(cache hit)* | 10 ms | 30 ms | 80 ms | < 0.5 % | Redis lookup only. |
| `GET /bookings/search` *(cache miss)* | 200 ms | 500 ms | 1 000 ms | < 1 % | Falls through to Duffel — bound by their SLO, not ours. |

The bolded **p95 < 150 ms** for `POST /bookings` is the load-bearing SLO — the booking flow is the 100 rps capacity requirement and the user-facing latency contract.

### 4.2 Availability and error budget

| Metric | Target | Monthly budget |
|---|---|---|
| Availability (non-error response, any latency) | 99.9 % | ~43 min downtime |
| Error rate (5xx + dependency failures) | < 0.1 % | ~30 errors / 5 min @ 100 rps |
| Latency SLO compliance (p95 within target per endpoint) | 99.0 % of 5-min windows | ~7 windows / month outside SLO |

99.9 % availability is the realistic target for this stack — going to 99.95 % would need cross-region replication (RDS read replica failover, multi-region SQS) and isn't justified by the use case. Document, don't pursue.

### 4.3 SLI measurement (the "how")

Per-endpoint percentiles weren't being computed before — Spring's `http.server.requests` only emitted count/sum/max by default. **Code change applied** in `application.yaml`:

```yaml
management:
  metrics:
    distribution:
      percentiles-histogram:
        http.server.requests: true
      percentiles:
        http.server.requests: 0.5, 0.95, 0.99
      slo:
        http.server.requests: 50ms, 100ms, 150ms, 300ms, 500ms, 1s
```

This makes Micrometer record full histograms for `http.server.requests` (with the route as the `uri` tag), then export per-route p50/p95/p99 gauges *and* per-bucket counters via the OTel collector to CloudWatch. Dashboards can now answer "what fraction of `POST /bookings` requests stayed under 150 ms in the last 5 min?" — the SLO question.

Histogram overhead: ~1 KB per (uri, status) tag combo. At ~20 endpoints × 5 status codes = ~100 series ≈ 100 KB resident. Trivial.

### 4.4 Alarm alignment

Existing CloudWatch alarms in `docs/alerting/cloudwatch-alarms.json` were tuned before SLOs were defined; their thresholds didn't match the SLO contract. **Updates applied**:

| Alarm | Before | After | Rationale |
|---|---|---|---|
| `workflow-service-5xx-rate` | Sum > 1 in 5 min (P1) | Sum > 5 in 5 min (P1) | At 100 rps = 30 k req/5 min, 1 error is 0.003 % — too sensitive (single transient error pages). 5 errors = 0.017 %, still 6× below the 0.1 % SLO budget. |
| `workflow-service-p99-latency` | p99 > **2 s** for 5 min (P2) | p99 > **1 s** for 5 min (P2) | The SLO ceiling for the worst path (search cache-miss) is 1 s. 2 s threshold meant the alarm only fired after the SLO had been broken for some time. |
| *(new)* `workflow-service-p95-latency` | n/a | p95 > **200 ms** for 10 min (P2) | Leading indicator on the booking hot path. SLO is 150 ms — 200 ms with 2× evaluationPeriods catches sustained breaches without firing on single noisy windows. |

The ALB-level metric (`TargetResponseTime`) aggregates across all routes, so these alarms are coarse-grained — a slow Duffel cache-miss could mask a fast booking endpoint, or vice versa. **Per-endpoint SLO alarms** require alarming on the Micrometer-derived metrics (now available via §4.3) — listed in 4.5 as deferred.

### 4.5 Deferred

- **Per-endpoint SLO alarms** (e.g. p95 of `POST /bookings` specifically) — needs CloudWatch alarms on the OTel-exported Micrometer metrics, with metric filters or Embedded Metric Format. Not blocking; the ALB-level alarms catch coarse degradation, and the dashboards already show per-endpoint percentiles thanks to §4.3.
- **Multi-window multi-burn-rate error budget alarms** — Google SRE pattern: alert at fast-burn (1 h consuming 5 % of monthly budget) AND slow-burn (6 h consuming 10 %). Useful at 99.95 % SLOs; overkill at 99.9 %.
- **Real-user latency vs synthetic** — JMeter (§5) measures from the load-gen box. Adding RUM / browser-side timings would surface end-to-end latency including TLS, ALB hop, and ISP variance, not just server-side.

---

## 5. What the local JMeter run validates (and doesn't)

The `mvn -Pperf verify` plan in `src/test/jmeter/workflow-load.jmx` runs against a Docker-Compose-backed local stack (Postgres + Redis + LocalStack). It's useful for some questions and misleading for others; this section is the contract for which is which.

### 5.1 What translates from local to prod

These are properties of the code or workload, not the environment, so local numbers carry over directly:

| Property | Why it translates |
|---|---|
| **Correctness under load** — error rate, deadlocks, connection leaks | Same code path, same locking primitives, same Hikari config. A leak that surfaces locally surfaces in prod. |
| **Outbox queue growth shape** — does drain rate keep up with inflow? | Function of `CLAIM_BATCH_SIZE` × poll cadence vs ingest rate. Independent of where the DB lives. If queue depth grows under steady-state local load, it'll grow in prod. |
| **HikariCP saturation** as a *fraction* of pool | If local active conns hit 80 % of pool at 100 rps, prod hits the same fraction (DB latency cancels out in the ratio: `concurrent = rps × per_query_time`). Absolute pool size still needs prod-side validation. |
| **GC pause distribution** as a fraction of wall time | Same JVM, same heap size, same GC algorithm. Prod GC profile matches local within a few %. |
| **JIT warm-up shape** — when does throughput stabilise? | Same bytecode, same JIT compiler. Local timing tells you how many requests until p95 stabilises. |
| **Relative regression** — "this PR moved p95 from X to Y" | Same delta direction. Don't compare absolute X across PRs; do compare X→Y within one. |

### 5.2 What does NOT translate

These are dominated by the environment, not the code:

| Property | Local | Prod (eu-west-1, Multi-AZ) | Why it diverges |
|---|---|---|---|
| **p50 latency** | ~5–15 ms | ~30–50 ms | Local DB is co-located in Docker (no network hop); prod has VPC-internal hop + Multi-AZ sync replication on every commit. ~3–5× gap. |
| **p95 latency** | ~20–50 ms | ~80–150 ms | Same gap factor as p50 plus tail-amplifying network jitter. |
| **p99 latency** | dominated by local GC (~50–200 ms) | dominated by network jitter + GC (~150–300 ms) | Different dominant cause; tail is always longer in prod. |
| **Throughput ceiling** | bounded by laptop CPU + Docker overhead | usually higher in prod (Graviton, real EBS, parallel I/O) | Local often can't push past ~300 rps before laptop CPU saturates; prod tier sustains far more. **Local stress run does not measure prod ceiling.** |
| **HikariCP exhaustion threshold (in absolute rps)** | depends on Docker DB config | depends on `db.r6g.large` characteristics | Must be re-validated against stage. |
| **Connection-establish latency** | ~1 ms | ~5 ms (TLS + cross-AZ) | Hikari's `min-idle=5` masks this in steady state but it shows up after `idle-timeout` reclamation. |
| **External SLAs** — Cognito JWKS, Duffel, real SQS | not exercised (LocalStack is fast and lossless) | real SLAs apply | LocalStack SQS p99 is ~2 ms; AWS SQS p99 is ~50 ms. |

**Rule of thumb**: trust local for *shapes* and *ratios*, never for *absolute numbers*.

### 5.3 Acceptance gates for the local JMeter run

When you run `mvn -Pperf verify -DskipTests -DskipITs`, look at the generated `target/jmeter/reports/workflow-load/index.html` and check:

| Gate | Threshold | Validates |
|---|---|---|
| Zero 5xx during **steady** (20 rps, 60 s) | 0 errors | Functional correctness; baseline path is healthy |
| Zero 5xx during **peak** (100 rps, 5 min) | 0 errors | The 100 rps target itself, error-rate-wise |
| Outbox queue depth stable during peak | growing then plateauing < ~50 rows | §2.3 drain-rate math holds in practice |
| HikariCP `active` peak | < 80 % of pool (16 / 20) | §1.2 pool-size math holds |
| Hikari leak-detection warnings | 0 | No held-too-long connections (catches `@Transactional` mistakes) |
| GC pause sum during peak | < 10 % of wall-clock | §2.6 G1GC tuning is sufficient |
| **Stress** (200 rps, 2 min): error rate degradation expected | identify what fails first (DB conn? CPU? local Postgres?) | Establishes the "what hurts first" boundary — useful even though the absolute breakpoint differs from prod |

The booking endpoint p95 / p99 numbers from local **don't** validate the §4 SLO targets — they only confirm the shape (no growth over time) and absence of errors.

### 5.4 How to actually validate the SLOs

Local can't, by construction. The validation ladder:

1. **Local** — `mvn -Pperf verify` against Docker stack: smoke test, regression check, functional load test. Cheap, fast, runs on every dev machine.
2. **Stage** — same JMX with `-Dperf.host=<stage-alb>`. Real RDS Multi-AZ, real ElastiCache, real SQS. **This is where the §4 SLO numbers are verified.** The JMX is already parameterised for this — no profile changes needed.
3. **Prod canary** — small-percentage traffic shift with the new task definition. Ground truth, but requires the alarms from §4.4 to be live first.

Don't skip step 2: local-passing → prod-deploying without stage measurement has burned every team that's tried it. Especially relevant here because RDS Multi-AZ commit latency (the dominant per-request cost) doesn't exist in the local stack.

### 5.5 Closing the loop

Each earlier dimension has a measurement counterpart in §5.3:

| Dimension | Local check |
|---|---|
| §1.2 HikariCP sizing | "Hikari `active` < 80 % of pool" |
| §2.1 Outbox bloat | "Outbox queue depth stable; no unbounded growth" *(plus run for >24 h to see retention cleanup fire)* |
| §2.2 Async logging | No latency cliff at peak vs steady; logs continue flowing |
| §2.3 Drain rate | Outbox depth doesn't grow during peak |
| §3 Autoscaling | N/A — local has 1 task; only stage / prod can validate scale-out |
| §4 SLO targets | Shape only — no errors, no growth. Absolute numbers belong in stage. |
