## Summary

<!-- 1–3 bullets on what this PR does and why. Link to the issue / ticket if any. -->

## Definition of Done

The same checklist lives in [README.md → Definition of Done](../README.md#definition-of-done). Tick each "always" item; tick the conditional sections that apply.

### Always

- [ ] `mvn verify` green locally (102 unit + 7 IT + 9 Cucumber)
- [ ] `cd ui && npx tsc --noEmit && npm test` green (39 tests)
- [ ] New code paths covered by some test — unit / slice / IT / BDD as appropriate

### If you touched the booking-create hot path

Paths: `src/main/**/booking/**`, `application/outbox/**`, `application/aws/BookingEventConsumer*`

- [ ] Smoke-perf'd against the local stack:
      ```
      bash local/run-local.sh
      rm -rf target/jmeter/reports && mvn -Pperf verify -DskipTests -DskipITs \
        -Dperf.steady.duration=10 -Dperf.peak.duration=10 -Dperf.stress.duration=5
      ```
- [ ] Summary line reports `Err: 0 (0.00%)` and `Avg < 50ms`. (Full SLO validation runs on stage; this is the regression gate.)

### If you touched the OpenAPI spec (`src/main/resources/static/openapi.yaml`)

- [ ] `mvn compile` regenerates stubs without errors; the delegate impl still satisfies the interface
- [ ] [`insomnia/workflow-service-collection.yaml`](../insomnia/workflow-service-collection.yaml) updated so the new/changed endpoint has a request entry (reuse the `afterResponse` env-capture pattern where useful)

### If you added a Flyway migration

- [ ] Next free `V<N>__*.sql` (current head is `V8`)
- [ ] Repository / IT coverage for any new column or index (mirror `BookingRepositoryIT`)
- [ ] JPA entity annotations match the migration — the test profile uses `ddl-auto=create-drop` and won't run Flyway

### If you changed cross-component flow / public API

- [ ] README endpoint table updated
- [ ] `CLAUDE.md` Key-Design bullet added or revised
- [ ] `docs/design/ARCHITECTURE.md` updated for sections affected (workflow loop, trip grouping, state machine, observability)

## Test plan

<!-- How a reviewer can verify this end-to-end. Include curl / Insomnia steps,
     UI flows to walk through, or which span names to look for in Jaeger. -->
