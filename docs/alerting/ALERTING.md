# Alerting & Monitoring Strategy

## Philosophy

All alerting is based on symptoms (what the user experiences), not causes (what broke internally). Alerts are routed by severity:
- **P1** → PagerDuty on-call rotation (page immediately)
- **P2** → Slack `#workflow-service-alerts` (acknowledge within 30 min)

## CloudWatch Alarms

See `cloudwatch-alarms.json` for the full CDK-compatible definitions.

| Alarm | Threshold | Severity | Action |
|---|---|---|---|
| `workflow-service-5xx-rate` | ALB 5xx > 1% over 5 min | P1 | PagerDuty |
| `workflow-service-p99-latency` | Target p99 > 2000ms over 5 min | P2 | Slack |
| `workflow-service-ecs-cpu` | ECS CPU > 80% sustained 10 min | P2 | Slack |
| `workflow-service-rds-storage` | RDS free storage < 10 GB | P2 | Slack |
| `workflow-service-sqs-age` | SQS oldest message > 60s | P2 | PagerDuty |
| `workflow-service-rds-connections` | RDS connections > 180 | P2 | Slack |

## CloudWatch Dashboard

See `cloudwatch-dashboard.json` for the full dashboard definition.

Dashboard name: `workflow-service-production`

Widgets:
1. **Requests / min** — ALB `RequestCount` (1-min sum)
2. **Error rates** — ALB `HTTPCode_Target_5XX_Count` / `HTTPCode_Target_4XX_Count`
3. **Latency percentiles** — ALB `TargetResponseTime` p50 / p95 / p99
4. **ECS utilisation** — `CPUUtilization` + `MemoryUtilization` per service
5. **Booking creation rate** — custom metric `booking.created.count` (Micrometer → CloudWatch)
6. **Cache hit ratio** — custom metric `search.cache.hit.ratio` (Micrometer)
7. **RDS connections** — `DatabaseConnections`
8. **RDS free storage** — `FreeStorageSpace`
9. **SQS queue depth** — `ApproximateNumberOfMessagesVisible` on `booking-events.fifo`

## Custom Metrics (Micrometer)

The service publishes custom metrics via Micrometer + Spring Actuator → CloudWatch Metrics Publisher:

```java
// In BookingService — increment on each successful booking
meterRegistry.counter("booking.created", "resourceType", booking.getResourceType().name()).increment();

// In SearchService — track cache hits
meterRegistry.counter("search.cache", "result", cacheHit ? "hit" : "miss").increment();
```

Add `micrometer-registry-cloudwatch2` dependency to publish to CloudWatch automatically.

## Log Strategy

Structured JSON logging via Logback. Log levels:
- `WARN`/`ERROR` → always written + CloudWatch Metric Filter → `workflow-service-error-count` metric
- `INFO` → business events (booking created, employee registered)
- `DEBUG` → dev/local profile only

CloudWatch Insights query for 5xx investigation:
```
fields @timestamp, @message
| filter @message like /ERROR/
| sort @timestamp desc
| limit 100
```

## Runbook Links

- **5xx spike**: Check ECS task logs (`/ecs/workflow-service`), verify RDS connections < max, check recent deploy
- **p99 latency spike**: Check Redis cache hit ratio, RDS slow query log, ECS CPU utilisation
- **SQS age growing**: Check Lambda NotificationProcessor CloudWatch logs for errors, verify DLQ
- **RDS connections near max**: Verify HikariCP pool settings, consider RDS Proxy for connection multiplexing
