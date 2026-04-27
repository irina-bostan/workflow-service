-- Workflow outcome columns. Populated by the SQS consumer when the upstream
-- provider acknowledges (provider_ref) or rejects (cancellation_reason) a booking.
-- Both are nullable; PENDING rows have neither set.
ALTER TABLE bookings
    ADD COLUMN provider_ref VARCHAR(100),
    ADD COLUMN cancellation_reason VARCHAR(500);
