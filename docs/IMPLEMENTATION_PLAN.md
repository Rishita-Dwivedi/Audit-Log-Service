# Implementation Plan — Audit Log Service

Tracks actual implementation progress, phase by phase, against the plan in `docs/REQUIREMENTS.md` (Scenario A/B/C) and `docs/EVALUATION_CLOSURE_MATRIX.md` (previous-evaluation closure items). Updated as each phase completes — not written once at the end.

## Phase 1 — Project Foundation + Scenario A Core Domain — COMPLETE (2026-08-20)

**Scope:** Spring Boot project skeleton, layered architecture (controller/service/repository/entity/dto/exception/security/hash/domain), the audit event write/query APIs, the SHA-256 hash chain, chain verification, and the concurrency-control mechanism for the write path. Explicitly excluded from this phase (per direct instruction): Scenario B (retention, redaction, export), Scenario C (compliance reporting), the external chain-head anchor, and any `docs/EVALUATION_CLOSURE_MATRIX.md` items beyond what Phase 1 naturally touches (append-only enforcement, concurrency).

**What was built:**
- Maven/Spring Boot 3.2.5 project on Java 21, H2 (file-mode dev, in-memory test) + Flyway (`DECISIONS.md` ADR-001).
- `AuditRecordEntity` + `V1__init_schema.sql` (Scenario A columns only — Scenario B columns deferred to a later migration, see `docs/ARCHITECTURE.md`).
- `HashChainService` + `PayloadCanonicalizer` (`com.auditlog.hash`), unit-tested independently of Spring/the database.
- `AuditEventService` (write path, with the `chain_head` pessimistic-lock concurrency mechanism — `DECISIONS.md` ADR-011), `AuditQueryService` (read path, keyset pagination), `ChainVerificationService`.
- `AuditEventController` (`POST/GET /audit/events`, `GET /audit/events/{id}`), `AuditVerifyController` (`GET /audit/verify`).
- `GlobalExceptionHandler` for validation/not-found/malformed-body/bad-date errors.
- Forward-declared security types (`com.auditlog.security.AuthenticatedPrincipal`, `ResourceAuthorization`) — not wired into any request path (`docs/SECURITY.md`).
- 24 tests (9 unit, 15 integration) — see `docs/TESTING.md` and `docs/ENDPOINT_TEST_MATRIX.md`.

**Bugs found and fixed during this phase** (see `AI_USAGE_LOG.md` for the full account):
1. Hibernate schema-validation mismatches between Flyway-created column types (`CLOB`, `CHAR(64)`) and JPA-inferred expectations — fixed by using `@Lob` for the payload converter and `VARCHAR(64)` for hash columns.
2. `ChainVerificationService` reported `CONTENT_MISMATCH` instead of the more specific `LINKAGE_BROKEN` when `previous_hash` was tampered directly, because `previous_hash` itself feeds `record_hash`'s computation — fixed by checking linkage before content.
3. A genuine timestamp-precision bug: hashing the in-memory, full-precision timestamp before persistence, then recomputing the hash from the DB-round-tripped (lower-precision) value at verify time, produced false `CONTENT_MISMATCH` violations on every record with sub-millisecond precision — fixed by truncating to milliseconds before hashing, closing the gap regardless of the database's actual storage precision.

**Evidence:** `mvn test` → `Tests run: 24, Failures: 0, Errors: 0` (BUILD SUCCESS). `mvn clean package` → BUILD SUCCESS. Live run: packaged jar started, two events written via `curl`, `/audit/verify` confirmed intact, a record tampered directly via the H2 shell (bypassing the app), `/audit/verify` re-run and correctly reported `CONTENT_MISMATCH` with the right `sequenceNo` and `recordId`.

**Not done in this phase (explicitly, not an oversight):** Scenario B (retention/redaction/export), Scenario C (compliance reporting), the external chain-head anchor (`EVALUATION_CLOSURE_MATRIX.md` item 14), the range-verification endpoint (`GET /audit/verify?fromSeq=&toSeq=`), any authentication/authorization enforcement, JaCoCo coverage tooling (item 2), database fault/rollback tests (item 11), and everything else tracked in `docs/EVALUATION_CLOSURE_MATRIX.md` that isn't specifically about append-only enforcement or write-path concurrency.

## Milestone 7 — Security / tenant authorization — COMPLETE (2026-08-20)

**Scope:** JWT authentication (`docs/DECISIONS.md` ADR-008, finalized from `Proposed` to `Accepted`), tenant isolation and role-based authorization (ADR-012), closing `docs/EVALUATION_CLOSURE_MATRIX.md` items 3 (`SEC-03`), 16 (`SEC-02`), and 21 (`TEST-04`) for the endpoints that exist so far (query, fetch, verify — redaction/export/compliance don't exist yet).

**What was built:**
- `JwtService`, `JwtAuthenticationFilter`, `AuditSecurityContext`, `Roles`, `DevAuthController` (`POST /dev/auth/token` — explicitly **not** a real auth endpoint, see `docs/SECURITY.md`) in `com.auditlog.security`.
- `V2__add_tenant_id.sql`: `tenant_id` added to `audit_record`, now part of the hashed content (`HashChainService` signature changed to include it).
- `AuditEventService` derives `tenantId` from the JWT only (never the request body). `AuditQueryService` scopes query/fetch to the caller's tenant unless `ROLE_AUDITOR`. `ChainVerificationService` requires `ROLE_AUDITOR`.
- 19 new tests: `JwtServiceTest` (6, unit), `SecurityAuthenticationTest` (6, integration — `SEC-02`), `TenantIsolationTest` (7, integration — `SEC-03`/`TEST-04`). Total: 43 tests, all passing.

**Bug found and fixed:** `AuditQueryService.search()`'s pagination cursor used a ternary (`page.isEmpty() ? afterSequenceNo : page.get(...).getSequenceNo()`) mixing `Long` and primitive `long` — Java's numeric-promotion rule auto-unboxes the `Long` branch even when selected, throwing NPE the moment a query legitimately returned zero results with no cursor. Never triggered in Phase 1 because no earlier test produced a genuinely empty result set; `TenantIsolationTest.queryIgnoresAttemptToRequestAnotherTenantAsNonAuditor` was the first. Fixed with an explicit if/else.

**Evidence:** `mvn test` → `Tests run: 43, Failures: 0, Errors: 0` (BUILD SUCCESS). Live run: two tenants via `/dev/auth/token`, cross-tenant query returned empty, `/audit/verify` returned 403 for `ROLE_USER` and 200 (`chainIntact: true`) for `ROLE_AUDITOR`.

**Not done in this milestone:** authorization for redaction/export/compliance endpoints (they don't exist yet — will follow this same pattern in Milestones 8-10); request/body limits, CORS, secret management, TLS, immutable DB permissions, operational monitoring (closure-matrix items 5-10, deferred to the later "Security + negative testing" milestone).

## Milestone 8 — Redaction / commitments — COMPLETE (2026-08-20)

**Scope:** structured redaction per `docs/DECISIONS.md` ADR-003 (`FR-B2`), extending the tenant-authorization pattern from Milestone 7 to the new redact endpoint.

**What was built:**
- `RedactionCommitmentService` (`com.auditlog.redaction`): per-field salted commitments computed for every top-level payload field at write time, tombstone formatting/parsing, and `verifyFieldCommitments()` -- the reconciliation step that catches a raw-payload tamper `record_hash` alone can't see (since `record_hash` is now computed from commitments, not raw payload).
- `HashChainService.computeRecordHash()` changed to take `field_commitments` instead of `payload` -- the core mechanism that lets redaction not invalidate `record_hash`.
- `V3__add_redaction_columns.sql`: `salt`, `field_commitments`, `status`, `redacted_fields`, `redacted_at`, `redacted_by`.
- `AuditRecordEntity.applyRedaction()` -- the one deliberate, narrowly-scoped exception to entity immutability (`ADR-010`).
- `RedactionService`/`RedactionController` (`POST /audit/events/{id}/redact`), tenant-authorized the same way as query/fetch (`ADR-012`): same tenant or `AUDITOR`, 404 for cross-tenant, idempotent re-redaction.
- `ChainVerificationService` now also runs `verifyFieldCommitments()` per record.
- `AuditEventResponse` gains `status`/`redactedFields`; deliberately does **not** expose `salt`/`fieldCommitments` (would enable offline brute-forcing of low-entropy redacted values).
- 16 new tests: `RedactionCommitmentServiceTest` (9, unit), `RedactionTest` (7, integration). Total: 59 tests, all passing.

**No new bugs found this milestone** -- the careful design pass (working out the verification-gap problem on paper before coding) paid off; all 16 new tests passed on the first `mvn test` run.

**Evidence:** `mvn test` → `Tests run: 59, Failures: 0, Errors: 0` (BUILD SUCCESS). Live run: wrote an event with an `accountNumber` field, redacted it, confirmed `recordHash` identical before/after in the raw JSON responses, `/audit/verify` still reported `chainIntact: true`.

**Not done in this milestone:** nested-field redaction (documented scope limitation, `ADR-003`); retention/archival, export, compliance (Milestones 9-10); the remaining closure-matrix P0/P1 items (JaCoCo, request limits, CORS, secrets, TLS, DB permissions, fault-injection tests, reproducible CI evidence -- Milestones 11-12).

## Milestone 9 — Retention / export — COMPLETE (2026-08-20)

**Scope:** `FR-B1` (retention/archival) and `FR-B3` (bulk export), plus closure-matrix item 15/26 (`ARC-03`, signed export manifests) and extending tenant authorization (item 3/21) to both new endpoints.

**What was built:**
- `RetentionService`/`RetentionController` (`POST /audit/retention/apply`, `AUDITOR`-only, no scheduler): soft-delete via `AuditRecordEntity.archive()` (`ACTIVE` → `ARCHIVED` only, never overwrites `REDACTED`), eligibility based on `recorded_at` (server truth, not caller-supplied `event_timestamp`).
- `ExportSigningService` (`com.auditlog.export`): EC P-256 / `SHA256withECDSA` asymmetric signing, fresh key pair per application startup (never persisted or committed -- documented trade-off in `ADR-013`).
- `ExportBundleService`/`ExportController` (`GET /audit/export`): builds a bundle scoped by `actorId`/`resourceId` (at least one required) and tenant, with a `chainContext` anchoring the first exported record via `hashOfLastRecordBeforeRange`, and a signature a recipient can verify using only the bundle's own published fields plus the published public key.
- 12 new tests: `RetentionTest` (6), `ExportTest` (6). Total: 71 tests, all passing.

**No new bugs found this milestone** -- both features worked on the first `mvn test` run, continuing the pattern from Milestone 8 that upfront design work (working out the canonical-manifest reproducibility and the redaction/archival status interaction on paper first) avoids implementation surprises.

**Evidence:** `mvn test` → `Tests run: 71, Failures: 0, Errors: 0` (BUILD SUCCESS). Live run: exported a real signed bundle via `curl` (inspected `recordHash`/`chainContext`/`signature` fields), applied retention with `windowDays=-1` and confirmed `archivedCount: 1`.

**Not done in this milestone:** compliance scenario (Milestone 10); the remaining closure-matrix P0/P1 items (JaCoCo, request limits, CORS, secrets, TLS, DB permissions, fault-injection tests, reproducible CI evidence -- Milestones 11-12); hard archival (`ADR-004` alternative, not pursued); Merkle-root chain-inclusion proof for exports (`ADR-013`, scoped limitation, unchanged from the original design).

## Remaining milestones — NOT STARTED

Per your roadmap, in order: Compliance scenario (Milestone 10) → Security + negative testing (Milestone 11) → JaCoCo + CI + final evidence (Milestone 12). Each gets its own PLAN → REVIEW → IMPLEMENT → TEST → REVIEW → DOCUMENT → COMMIT cycle and closure-matrix status update, same as Milestones 7-9 above.
