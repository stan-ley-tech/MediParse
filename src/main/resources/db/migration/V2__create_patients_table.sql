CREATE TABLE patients (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    mrn             VARCHAR(64)  NOT NULL UNIQUE,
    full_name       VARCHAR(255) NOT NULL,
    date_of_birth   DATE,
    sex             VARCHAR(16),
    created_by      UUID REFERENCES users (id),
    created_at      TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at      TIMESTAMPTZ  NOT NULL DEFAULT now()
);

CREATE INDEX idx_patients_full_name ON patients (full_name);
