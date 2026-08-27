CREATE TABLE extracted_entities (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    document_id     UUID NOT NULL REFERENCES documents (id) ON DELETE CASCADE,
    entity_type     VARCHAR(32) NOT NULL,
    label           VARCHAR(255) NOT NULL,
    value           VARCHAR(512),
    numeric_value   NUMERIC(12, 4),
    unit            VARCHAR(32),
    reference_range VARCHAR(64),
    status          VARCHAR(16),
    confidence      NUMERIC(3, 2) NOT NULL DEFAULT 1.0,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT chk_extracted_entities_type CHECK (entity_type IN
        ('PATIENT', 'DOCTOR', 'FACILITY', 'DATE', 'DIAGNOSIS', 'MEDICATION',
         'DOSAGE', 'LAB_TEST', 'LAB_RESULT', 'REFERENCE_RANGE', 'ALLERGY')),
    CONSTRAINT chk_extracted_entities_status CHECK (status IS NULL OR status IN
        ('NORMAL', 'HIGH', 'LOW', 'ABNORMAL'))
);

CREATE INDEX idx_extracted_entities_document_id ON extracted_entities (document_id);
CREATE INDEX idx_extracted_entities_entity_type ON extracted_entities (entity_type);
