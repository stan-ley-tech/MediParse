CREATE TABLE documents (
    id                      UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    patient_id              UUID REFERENCES patients (id),
    uploaded_by             UUID NOT NULL REFERENCES users (id),
    original_filename       VARCHAR(512) NOT NULL,
    content_type            VARCHAR(128) NOT NULL,
    file_size_bytes         BIGINT NOT NULL,
    file_hash               VARCHAR(64) NOT NULL,
    storage_path            VARCHAR(1024) NOT NULL,
    document_type           VARCHAR(32) NOT NULL DEFAULT 'UNKNOWN',
    status                  VARCHAR(32) NOT NULL DEFAULT 'UPLOADED',
    version_number          INT NOT NULL DEFAULT 1,
    parent_document_id      UUID REFERENCES documents (id),
    processing_error        TEXT,
    processing_attempts     INT NOT NULL DEFAULT 0,
    extracted_text_char_count INT,
    created_at              TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at              TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT chk_documents_type CHECK (document_type IN
        ('LAB_REPORT', 'PRESCRIPTION', 'DISCHARGE_SUMMARY', 'REFERRAL_LETTER', 'UNKNOWN')),
    CONSTRAINT chk_documents_status CHECK (status IN
        ('UPLOADED', 'QUEUED', 'PROCESSING', 'COMPLETED', 'FAILED')),
    CONSTRAINT uq_documents_uploader_hash UNIQUE (uploaded_by, file_hash)
);

CREATE INDEX idx_documents_patient_id ON documents (patient_id);
CREATE INDEX idx_documents_status ON documents (status);
CREATE INDEX idx_documents_document_type ON documents (document_type);
CREATE INDEX idx_documents_created_at ON documents (created_at);
CREATE INDEX idx_documents_parent_document_id ON documents (parent_document_id);
