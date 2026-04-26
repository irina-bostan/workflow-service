CREATE TABLE employees (
    id                  UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
    employee_id         VARCHAR(20)  NOT NULL UNIQUE,
    first_name          VARCHAR(100) NOT NULL,
    last_name           VARCHAR(100) NOT NULL,
    email               VARCHAR(255) NOT NULL UNIQUE,
    department          VARCHAR(100) NOT NULL,
    cost_centre_default VARCHAR(50),
    created_at          TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at          TIMESTAMPTZ  NOT NULL DEFAULT now(),
    version             BIGINT       NOT NULL DEFAULT 0
);

CREATE INDEX idx_employees_employee_id ON employees (employee_id);
CREATE INDEX idx_employees_email       ON employees (email);
