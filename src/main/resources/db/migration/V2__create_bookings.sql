CREATE TABLE bookings (
    id              UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
    employee_id     VARCHAR(20)  NOT NULL,
    resource_type   VARCHAR(20)  NOT NULL,
    destination     VARCHAR(50)  NOT NULL,
    departure_date  TIMESTAMPTZ  NOT NULL,
    return_date     TIMESTAMPTZ,
    traveler_count  INTEGER      NOT NULL CHECK (traveler_count >= 1),
    cost_center_ref VARCHAR(50)  NOT NULL,
    trip_purpose    VARCHAR(500),
    idempotency_key VARCHAR(255) UNIQUE,
    created_at      TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at      TIMESTAMPTZ  NOT NULL DEFAULT now(),
    version         BIGINT       NOT NULL DEFAULT 0,
    CONSTRAINT fk_bookings_employee FOREIGN KEY (employee_id)
        REFERENCES employees (employee_id) ON DELETE RESTRICT
);

CREATE INDEX idx_bookings_employee_id   ON bookings (employee_id);
CREATE INDEX idx_bookings_departure_date ON bookings (departure_date);
