-- Trip-grouping for bookings. A trip is a set of bookings (typically a flight + hotel)
-- that share a trip_id; a user-initiated cancel cascades across all siblings via
-- BookingService.cancelByUser. Existing rows get a unique trip_id via the default so
-- each becomes a "trip of one" — the cascade is a no-op for them.
ALTER TABLE bookings
    ADD COLUMN trip_id UUID NOT NULL DEFAULT gen_random_uuid();

-- Index for the sibling lookup (cancel cascade and the GET /trips/{id}/bookings endpoint).
CREATE INDEX idx_bookings_trip_id ON bookings (trip_id);
