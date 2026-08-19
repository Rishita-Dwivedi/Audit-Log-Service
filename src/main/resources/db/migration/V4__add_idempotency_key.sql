-- Replay/idempotency protection (docs/EVALUATION_CLOSURE_MATRIX.md item 4, SEC-06). NULL values
-- are always distinct under standard unique-index semantics, so multiple requests that omit an
-- idempotency key are unaffected by this constraint -- only a genuine duplicate (same tenant,
-- same key) is rejected at the database level as a second line of defense behind the
-- application-level check.
ALTER TABLE audit_record ADD COLUMN idempotency_key VARCHAR(200);
CREATE UNIQUE INDEX uq_audit_record_tenant_idempotency_key ON audit_record (tenant_id, idempotency_key);
