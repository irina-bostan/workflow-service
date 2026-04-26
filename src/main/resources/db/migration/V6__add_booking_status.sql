-- Booking lifecycle column. Default 'PENDING' at insert time; existing rows are backfilled
-- so the column can be NOT NULL without a separate backfill step.
ALTER TABLE bookings
    ADD COLUMN status VARCHAR(20) NOT NULL DEFAULT 'PENDING'
    CHECK (status IN ('PENDING', 'CONFIRMED', 'CANCELLED'));

-- Listing/filtering by status will become common; cheap to add now.
CREATE INDEX idx_bookings_status ON bookings (status);
