-- Structured redaction (docs/EVALUATION_CLOSURE_MATRIX.md item 8/Scenario B; docs/DECISIONS.md
-- ADR-003). salt and field_commitments are set once at write time and never change, including
-- through redaction -- this is what lets record_hash (computed from field_commitments, not raw
-- payload) survive a later redaction unchanged. Table is always empty when this migration runs
-- (dev/test only, no production data), so these are added as NOT NULL directly.
ALTER TABLE audit_record ADD COLUMN salt VARCHAR(64) NOT NULL;
ALTER TABLE audit_record ADD COLUMN field_commitments CLOB NOT NULL;
ALTER TABLE audit_record ADD COLUMN status VARCHAR(20) NOT NULL;
ALTER TABLE audit_record ADD COLUMN redacted_fields CLOB NOT NULL;
ALTER TABLE audit_record ADD COLUMN redacted_at TIMESTAMP WITH TIME ZONE;
ALTER TABLE audit_record ADD COLUMN redacted_by VARCHAR(200);

CREATE INDEX idx_audit_record_status ON audit_record (status);
