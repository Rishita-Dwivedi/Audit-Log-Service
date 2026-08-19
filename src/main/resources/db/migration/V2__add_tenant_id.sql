-- Tenant isolation (docs/EVALUATION_CLOSURE_MATRIX.md item 3, SEC-03). tenant_id is part of
-- a record's hashed content (see HashChainService) so it cannot be silently reassigned
-- without breaking the chain, same as any other field.
ALTER TABLE audit_record ADD COLUMN tenant_id VARCHAR(200);
UPDATE audit_record SET tenant_id = 'UNASSIGNED' WHERE tenant_id IS NULL;
ALTER TABLE audit_record ALTER COLUMN tenant_id SET NOT NULL;

CREATE INDEX idx_audit_record_tenant_id ON audit_record (tenant_id);
