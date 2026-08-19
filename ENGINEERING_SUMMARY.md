# Engineering Summary — Secure Audit Log Service

## Plan / rationale

Built incrementally per `docs/IMPLEMENTATION_PLAN.md`: Planning → Project Structure → Milestones 1-6 (Scenario A: domain, hashing, write/query/verify APIs, concurrency) → Milestone 7 (security/tenant auth) → 8 (redaction) → 9 (retention/export) → 10 (compliance/Scenario C) → 11 (security hardening) → 12 (this milestone). Every milestone: implement, run the real test suite, fix real failures, document, commit — never assumed working without running it.

## Artifacts

- **Code:** `src/main/java/com/auditlog/**` — 54 classes across entity/repository/hash/redaction/export/security/service/controller/dto/exception/config/domain packages.
- **Tests:** 83 tests (unit + integration), all passing. 91% instruction coverage / 77% branch coverage (JaCoCo, `target/site/jacoco/index.html`, threshold-enforced at 70%/60%).
- **Docs:** `docs/REQUIREMENTS.md`, `docs/ARCHITECTURE.md`, `docs/DECISIONS.md` (14 ADRs), `docs/SECURITY.md`, `docs/TESTING.md`, `docs/scenario-c.md`, `docs/IMPLEMENTATION_PLAN.md`, `docs/EVALUATION_CLOSURE_MATRIX.md`, `docs/ENDPOINT_TEST_MATRIX.md`, `AI_USAGE_LOG.md`, `ATTESTATION.md`, `README.md`.
- **Git history:** real, incremental — one commit per milestone (see `git log`), not a single mega-commit (the one exception, Phase 1, was caught and reconstructed into per-milestone commits — see `AI_USAGE_LOG.md`).

## What was built (by scenario)

- **Scenario A:** append-only write/query/verify APIs, SHA-256 hash chain over per-field commitments (not raw payload — this is what makes redaction possible later), pessimistic-lock concurrency control, tamper detection (content, linkage, sequence-gap).
- **Scenario B:** field-commitment redaction (record_hash survives redaction unchanged), soft-delete retention, asymmetrically-signed export bundles (`SHA256withECDSA`).
- **Scenario C:** `docs/scenario-c.md` written before any code — 6 identified ambiguities, 6 stated assumptions, a distinct `COMPLIANCE_OFFICER` role, mandatory (never defaulted) time range.
- **Security:** JWT authentication, tenant isolation (BOLA/IDOR prevention, 404-not-403 for cross-tenant), idempotency/replay protection, request-size limits, CORS, health monitoring.

## Risks / trade-offs / validation

Every non-trivial decision is recorded as an ADR in `docs/DECISIONS.md` with alternatives considered — not repeated here. Headline trade-offs: H2 over Postgres (time/environment, ADR-001); ephemeral in-memory export-signing key (no secret manager available, ADR-013); soft-delete not hard archival (ADR-004); newest-tail deletion still undetectable (ARC-02, not addressed); a genuine tension found between "immutable DB permissions" and redaction/archival's legitimate need to `UPDATE` records (ADR-014, documented not implemented).

## Assumptions

Documented per-decision in `docs/DECISIONS.md` and, for Scenario C specifically, in `docs/scenario-c.md` with the rejected alternative named for each.

## Limitations (explicitly scoped out, not silently dropped)

- TLS/HTTPS deployment evidence (item 8) and full immutable DB permissions (item 9) — documented in `docs/DECISIONS.md` ADR-014, not implemented, given remaining time.
- Database fault/rollback tests (item 11), PII/log-injection tests (item 18), endpoint-test-matrix coverage annotations (item 19), expanded boundary testing (item 20), external chain-head anchor (item 14/25) — not started.
- Nested-field redaction, hard archival, Merkle-root chain-inclusion proof for exports, real regulator delivery — all explicitly scoped out in their respective ADRs.

See `docs/EVALUATION_CLOSURE_MATRIX.md` for the complete, current status of all 26 previous-evaluation findings.
