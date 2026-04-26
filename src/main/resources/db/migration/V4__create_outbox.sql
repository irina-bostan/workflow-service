-- Transactional outbox: events are written in the same transaction as the
-- aggregate they describe, then a relay polls and publishes to SQS.
-- Eliminates the dual-write hole between the bookings INSERT and the SQS publish.

CREATE TABLE outbox (
    id              UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
    aggregate_type  VARCHAR(50)  NOT NULL,
    aggregate_id    UUID         NOT NULL,
    event_type      VARCHAR(100) NOT NULL,
    payload         TEXT         NOT NULL,
    status          VARCHAR(20)  NOT NULL DEFAULT 'PENDING',
    attempts        INTEGER      NOT NULL DEFAULT 0,
    last_error      TEXT,
    created_at      TIMESTAMPTZ  NOT NULL DEFAULT now(),
    last_attempt_at TIMESTAMPTZ,
    sent_at         TIMESTAMPTZ
);

-- Partial index: relay queries are always WHERE status='PENDING'
CREATE INDEX idx_outbox_pending ON outbox (created_at) WHERE status = 'PENDING';
