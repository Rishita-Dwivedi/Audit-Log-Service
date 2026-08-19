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

## Phase 2 and beyond — NOT STARTED

To be planned and approved before implementation begins, per the same PLAN → REVIEW → IMPLEMENT → TEST → REVIEW → DOCUMENT → COMMIT discipline as Phase 1. Candidate next steps (not yet sequenced or approved):
- Scenario B: retention/archival, structured redaction (field-commitment scheme), bulk export.
- Security: resolve the ADR-008 auth-mechanism decision (currently `Proposed`), then implement and test it, then build the tenant/resource-authorization model (`EVALUATION_CLOSURE_MATRIX.md` item 3) on top of it.
- Scenario C: clarification document first, then scoped design and implementation.
- Remaining P0/P1 closure-matrix items not touched by Phase 1: JaCoCo coverage (item 2), request/body limits and CORS (items 5-6), secret management and TLS (items 7-8), immutable DB permissions (item 9, contingent on the H2/Postgres decision), database fault/rollback and reproducible CI evidence (items 11, 13).
