CREATE TABLE processing_jobs (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    document_id     UUID NOT NULL REFERENCES documents (id),
    status          VARCHAR(32) NOT NULL DEFAULT 'PENDING',
    attempts        INT NOT NULL DEFAULT 0,
    max_attempts    INT NOT NULL DEFAULT 3,
    last_error      TEXT,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
    started_at      TIMESTAMPTZ,
    completed_at    TIMESTAMPTZ,
    CONSTRAINT chk_processing_jobs_status CHECK (status IN
        ('PENDING', 'IN_PROGRESS', 'COMPLETED', 'FAILED'))
);

-- A document can only have one job actively pending or running at a time,
-- which is what keeps duplicate submissions and re-queues from double-processing.
CREATE UNIQUE INDEX uq_processing_jobs_active_per_document
    ON processing_jobs (document_id)
    WHERE status IN ('PENDING', 'IN_PROGRESS');

CREATE INDEX idx_processing_jobs_status ON processing_jobs (status);
