CREATE TABLE appointments (
    id               UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
    booking_id       UUID         NOT NULL,
    appointment_type VARCHAR(30)  NOT NULL,
    scheduled_at     TIMESTAMPTZ  NOT NULL,
    notes            VARCHAR(1000),
    created_at       TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at       TIMESTAMPTZ  NOT NULL DEFAULT now(),
    version          BIGINT       NOT NULL DEFAULT 0,
    CONSTRAINT fk_appointments_booking FOREIGN KEY (booking_id)
        REFERENCES bookings (id) ON DELETE CASCADE
);

CREATE INDEX idx_appointments_booking_id  ON appointments (booking_id);
CREATE INDEX idx_appointments_scheduled_at ON appointments (scheduled_at);
