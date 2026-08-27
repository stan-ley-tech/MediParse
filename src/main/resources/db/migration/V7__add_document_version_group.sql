-- Versions of the same logical document share a version_group_id (the id of
-- the first version), which lets us list a whole version history without
-- walking the parent_document_id chain.
ALTER TABLE documents ADD COLUMN version_group_id UUID;
UPDATE documents SET version_group_id = id WHERE version_group_id IS NULL;
ALTER TABLE documents ALTER COLUMN version_group_id SET NOT NULL;

CREATE INDEX idx_documents_version_group_id ON documents (version_group_id);
