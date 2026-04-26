-- Aggressive per-table autovacuum settings for the outbox.
-- At 100 rps the table sees ~720 k row mutations per hour (UPDATEs PENDING->SENT
-- plus DELETEs from the retention cleanup). PostgreSQL defaults
-- (autovacuum_vacuum_scale_factor=0.2) wait for dead tuples to reach 20% of the
-- table before vacuuming -- on a ~8.6M-row steady-state, that's ~1.7M dead
-- tuples and ~2.4 h between cycles, with corresponding heap bloat.
--
-- Tighter settings keep dead-tuple % below 5% (~430k dead tuples), so vacuum
-- cycles run every ~30-40 min and the heap stays bounded around ~50 MB of
-- unreclaimed space. The hot path is unaffected -- the partial index
-- idx_outbox_pending only contains PENDING rows so it's tiny regardless.
ALTER TABLE outbox SET (
    autovacuum_vacuum_scale_factor   = 0.05,
    autovacuum_vacuum_threshold      = 1000,
    autovacuum_analyze_scale_factor  = 0.05,
    autovacuum_analyze_threshold     = 1000
);
