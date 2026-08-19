# Engineering Decisions — Audit Log Service

Lightweight architecture-decision records. Each entry: context, decision, alternatives considered, trade-offs/consequences, status. Status `Accepted` means explicitly confirmed by the engineer; `Proposed` means an AI-suggested default awaiting explicit confirmation before implementation relies on it (see `AI_USAGE_LOG.md`). Requirement IDs referenced are defined in `REQUIREMENTS.md`.

---

## ADR-001 — Persistence: H2 only

**Status:** Accepted (2026-08-20, reaffirmed 2026-08-20 with expanded rationale)

**Context:** The service needs a database to demonstrate the assignment's core validation step — write, query, verify, tamper directly in the data store, verify again (`FR-A4`). The choice of database affects setup friction, test infrastructure, and how convincing the tamper demo is. The previous engineering evaluation separately flagged immutable-database-permissions and PostgreSQL/Testcontainers reproducibility as gaps (`docs/EVALUATION_CLOSURE_MATRIX.md` items 9, 11, 13, 22) — this ADR's expanded content addresses those directly rather than silently treating H2 as equivalent to Postgres.

**Decision:** Use H2 for the current runnable prototype — file-mode for local development and the live tamper demo (so state persists across the app process and a separate tamper step, and the H2 web console/shell gives a direct way to hand-edit a stored row), in-memory for automated unit/integration tests. This is a **deliberate development/testing trade-off given the available time and environment**, made explicitly by the engineer, not an implicit default.

1. **Why H2 was selected for this environment.** The engineer directed that PostgreSQL/Testcontainers not be pursued for Phase 1: it would require a working Docker setup, and the engineer's stated priority was to keep the prototype runnable with a simple `mvn` command inside the assignment's time-box, rather than spend implementation time on Docker/Postgres environment setup. This report of environment constraints comes from the engineer directly; it was not independently re-verified by re-attempting a Docker/Testcontainers setup in this environment.
2. **What H2 allows demonstrating.** The full Scenario A validation loop end-to-end and for real: write via the API, query via the API, verify via the API, tamper a record's content directly in the on-disk H2 file (via the H2 shell/console, bypassing the application entirely), verify again and observe detection. This was executed live during Phase 1 implementation (not only via automated tests) — see `docs/TESTING.md`. It also allows genuine concurrent-write testing (H2 supports row-level pessimistic locking via `SELECT ... FOR UPDATE`, which the concurrency-control mechanism in ADR-011 depends on).
3. **What H2 cannot realistically demonstrate.** (a) Fine-grained, revocable per-statement database *permissions* restricting the application's own DB identity from issuing `UPDATE`/`DELETE` against `audit_record` — H2's privilege model is materially weaker than a server-grade RDBMS here; treating H2 as equivalent to Postgres on this point would misrepresent the guarantee. (b) True multi-instance/multi-process contention against a shared, network-accessed database server, as opposed to `AUTO_SERVER` file access from a single machine. (c) Reproducibility evidence via Testcontainers (`docs/EVALUATION_CLOSURE_MATRIX.md` item 11), since Testcontainers spins up a real containerized database and H2 has no equivalent role to play there.
4. **PostgreSQL production migration considerations.** Schema/DDL is written in reasonably portable standard SQL via Flyway migrations, but no work has been done to verify Postgres-specific behavior (e.g., `TIMESTAMP WITH TIME ZONE` semantics, `SELECT ... FOR UPDATE` lock behavior under Postgres's MVCC, JSON/JSONB column mapping) actually matches what was validated against H2. A real migration would need its own validation pass against a real Postgres instance before being trusted, not just a connection-string swap.
5. **Immutable database permissions — documented as a production limitation, not solved here.** See `docs/EVALUATION_CLOSURE_MATRIX.md` item 9 (`SEC-09`): the application's own H2 DB identity is not restricted from issuing `UPDATE`/`DELETE` against `audit_record` at the database-permission level. The only enforcement in Phase 1 is application-layer (no such endpoint exists — `ADR-010`) plus the hash chain's own detection capability (`ChainVerificationService`) — both of which were exercised and confirmed working live. A DB-identity-level permission restriction is explicitly **not implemented** and is not claimed to be.
6. **Testcontainers/PostgreSQL as a future task, if time permits.** Recorded as a scoped-out, not-abandoned item: `docs/EVALUATION_CLOSURE_MATRIX.md` items 11 and 22 track this as a production-compatibility validation task, distinct from the core Scenario A/B/C engineering work.

**Alternatives considered:**
- **PostgreSQL + Flyway + Testcontainers.** Closer to a production setup; the raw-SQL tamper demo would use `psql` directly. Not pursued for Phase 1 per the engineer's explicit time/environment trade-off decision above.

**Trade-offs / consequences:** H2 is not the production database of record in a real deployment — this is documented as a scoped simplification, not hidden, and specifically does **not** claim H2 provides the same database-permission guarantees as PostgreSQL (see point 5 above).

---

## ADR-002 — Timestamp handling and chain ordering

**Status:** Accepted (2026-08-20)

**Context:** The assignment requires a `timestamp` field on write (`FR-A1`) and asks the engineer to document whether it is caller-supplied or server-assigned. Separately, the hash chain needs an unambiguous ordering key (`FR-A3`).

**Decision:** Accept `timestamp` as caller-supplied and store it as `event_timestamp` (informational); it is a **required** field, not defaulted when absent, so the API contract makes explicit that the caller is asserting a value rather than the server silently filling one in. Separately stamp `recorded_at` server-side. The hash chain's order of truth is `sequence_no`, assigned atomically at insert time — not `event_timestamp`.

**Alternatives considered:**
- **Trust the caller-supplied timestamp directly for ordering.** Simpler (one fewer column), but lets a caller backdate an event to slot it anywhere in the chain's apparent history, and loses the distinct audit fact of "when the service actually recorded this." Rejected because it weakens the tamper-evidence guarantee the whole system exists to provide.
- **Make `timestamp` optional, server-assigned when absent.** Considered and rejected in favor of the simpler, unambiguous "always required, always caller-supplied" rule — a hybrid optional/fallback rule is an extra branch of behavior to document and test for no clear benefit here.

**Trade-offs / consequences:** Two timestamp columns instead of one adds minor schema complexity. In exchange, the chain's ordering guarantee does not depend on trusting caller-supplied data, and both facts ("when the event says it happened" vs. "when we saw it") remain available, which is generally useful for an audit system.

**Implementation addendum (found during Phase 1 build, 2026-08-20):** both `event_timestamp` and `recorded_at` are truncated to millisecond precision (`OffsetDateTime.truncatedTo(ChronoUnit.MILLIS)`) in `AuditEventService.append()` *before* being used to compute `record_hash` and *before* being persisted. This was not part of the original design — it was added after `ConcurrentAppendTest` (which uses `OffsetDateTime.now()`, carrying nanosecond precision) failed with every single record reporting `CONTENT_MISMATCH` on verification. Root cause: the hash was originally computed from the in-memory, full-precision timestamp value immediately before persistence, but reading the same record back from the database for verification returned a value whose precision had been altered by the JDBC/column round-trip — so `ChainVerificationService`'s recomputed hash used a different timestamp value than `AuditEventService`'s original computation, even though nothing had actually been tampered with. Fixed-timestamp tests (e.g. `"2026-01-01T00:00:00Z"`, zero sub-second precision) never exposed this, which is why it wasn't caught until the concurrency test ran. Truncating to a fixed, explicit precision **before** hashing closes the gap regardless of the database's actual storage precision, and is now a stated contract rather than an accidental dependency on round-trip fidelity. Verified via `ConcurrentAppendTest` (20 concurrent writes, `chainIntact: true` after the fix) and the full suite passing.

---

## ADR-003 — Redaction scheme

**Status:** Accepted (2026-08-20); implemented and tested (Milestone 8, 2026-08-20)

**Context:** `FR-B2` requires that flagged payload fields be redactable without invalidating `record_hash`, which was originally computed over the unredacted content. This is called out by the assignment itself as a genuine, non-trivial engineering problem.

**Decision:** Field-commitment scheme — at write time, compute a salted per-field commitment for **every** top-level payload field (not just ones a caller pre-flags; there is no API surface for declaring redactability up front, so any top-level field can be redacted later — see the scope note below), and hash **only the commitments**, never the raw payload directly. `HashChainService.computeRecordHash()` takes `field_commitments` where it used to take `payload`. Redaction (`RedactionCommitmentService`, `RedactionService`, `POST /audit/events/{id}/redact`) replaces the raw value with a tombstone embedding its (unchanged) commitment and never recomputes `record_hash` — record_hash was never a function of the raw value to begin with, only of the commitment, which redaction never touches.

**The verification gap this design creates, and how it's closed:** because `record_hash` no longer depends on the raw payload, recomputing `record_hash` alone can't catch someone tampering a raw payload value directly *without* also updating its commitment — `record_hash` would still match. `RedactionCommitmentService.verifyFieldCommitments()` closes this: for each non-redacted field it recomputes the commitment from the current raw value and salt and compares to the stored commitment (mismatch = tamper); for each redacted field it confirms the tombstone's embedded commitment matches what's stored (mismatch = forged/faked redaction). `ChainVerificationService` runs this for every record, in addition to the existing linkage/content/sequence checks.

**Alternatives considered:**
- **Encrypt-at-rest, redact via access control.** Keep the original value encrypted; "redaction" means hiding the decrypted value from unauthorized viewers rather than removing it. Materially simpler to build. Rejected as the primary approach because the original value still exists in storage — it satisfies a display-time privacy control but not a genuine erasure requirement (e.g., a real deletion obligation), which is closer to what "must be redactable to satisfy data privacy requirements" implies.
- **Recompute `record_hash` after redaction and store a redaction event as a separate append-only entry documenting the change.** Would keep the payload/hash relationship simple (hash always matches current content) but breaks the core guarantee that a record's hash, once written, is immutable evidence of its original content — effectively legalizes silent rewriting of history, which defeats the purpose of the chain.
- **Require callers to pre-declare which fields are redactable at write time** (an explicit API field). Considered and rejected in favor of committing every top-level field unconditionally: simpler API contract, no risk of forgetting to flag a field that later turns out to need redaction, and the cost (computing a few extra commitments per write) is negligible.

**Trade-offs / consequences:**
- **Scope: top-level payload fields only.** Nested object/array fields are not individually redactable in this milestone — redacting would require path-addressed commitments, judged disproportionate for the time available. Documented, not silently dropped.
- **`salt` and `field_commitments` are never exposed via the API** (`docs/DECISIONS.md`, `AuditEventResponse`'s Javadoc): exposing either would let a caller brute-force a redacted value offline for any low-entropy value space (e.g., a 4-digit PIN) by recomputing `SHA256(salt|field|guess)` for every candidate and comparing to the leaked commitment. This is a general limitation of any commitment-based redaction scheme, not just an implementation detail — it's inherent to the design and is stated here plainly rather than left implicit. An operator with direct database access still has this exposure; that is a documented, accepted risk for this prototype.
- Redaction is the **one deliberate exception** to `AuditRecordEntity`'s immutability (`ADR-010`): `applyRedaction()` mutates `payload`/`status`/`redacted_fields`/`redacted_at`/`redacted_by` only, never `record_hash`/`previous_hash`/`sequence_no`/`field_commitments`/`salt`.
- Redaction authorization follows the same tenant-scoping pattern as query/fetch (`ADR-012`): same tenant or `AUDITOR`, 404 (not 403) for cross-tenant, per `docs/EVALUATION_CLOSURE_MATRIX.md` item 3 (`SEC-03`).

**Validated by:** `RedactionCommitmentServiceTest` (9 unit tests) and `RedactionTest` (7 integration tests): redaction produces a tombstone and leaves `record_hash` unchanged; `/audit/verify` still passes after a legitimate redaction; a raw-payload tamper without updating the commitment is detected even though `record_hash` alone wouldn't catch it; a forged tombstone is detected; unknown-field and cross-tenant redaction requests are rejected; redacting an already-redacted field is idempotent. Also confirmed live: `recordHash` identical before/after redaction in a manual `curl` run.

---

## ADR-004 — Retention / archival mechanism

**Status:** Accepted (2026-08-20)

**Context:** `FR-B1` requires records older than a configurable window to be archivable/soft-deletable, and requires `/audit/verify` to not false-positive on legitimately archived records.

**Decision:** Soft-delete via a `status = ARCHIVED` flag. The row remains in `audit_record`; nothing about its stored content changes, so `/audit/verify` requires no special handling for archived records beyond recognizing the status.

**Alternatives considered:**
- **Hard archival**, moving rows out of the primary table (or exporting and purging them), with a synthetic "archival manifest" event (e.g. `eventType = SYSTEM_ARCHIVAL`) written into the chain so `/audit/verify` can recognize the resulting gap as legitimate rather than as tampering. More realistic for a real production retention policy, and a stronger demonstration of handling a genuine gap in the chain. Not chosen for this prototype given the 2–3 day time-box: it requires an additional table or export/purge mechanism, a manifest-verification code path, and its own test coverage, for a requirement that soft-delete already satisfies (`FR-B1`'s acceptance criteria only require no false positive on verify, which soft-delete meets directly).

**Trade-offs / consequences:** Soft-delete does not reduce storage or truly remove archived records from the live table, which a real retention/compliance policy might require. This is documented as a known limitation; the hard-archival alternative above is the documented path if that requirement surfaces later.

---

## ADR-005 — Hash algorithm

**Status:** Accepted (2026-08-20)

**Context:** `FR-A3` requires a hash of each record's content and of the preceding record.

**Decision:** SHA-256, via `java.security.MessageDigest`, no additional dependency.

**Alternatives considered:**
- **MD5 / SHA-1.** Both have known collision weaknesses; inappropriate for a tamper-evidence mechanism whose entire value proposition is collision resistance. Rejected.
- **SHA-3.** Cryptographically sound, but offers no advantage over SHA-256 for this use case, and is less universally the default choice reviewers would expect. No strong reason to deviate from the well-supported standard. Rejected in favor of SHA-256.

**Trade-offs / consequences:** None material — SHA-256 is a well-understood, fast, standard-library choice with no external dependency required.

---

## ADR-006 — JSON canonicalization for hashing

**Status:** Accepted (2026-08-20)

**Context:** `payload` is a structured JSON object. `record_hash` must be computed over a single deterministic byte representation of a record's content (`FR-A3`); if serialization is not deterministic, semantically identical payloads could hash differently, or worse, hash stability could depend on library/version behavior rather than content.

**Decision:** Canonicalize `payload` before hashing — sort object keys recursively, fix number formatting, fix encoding (UTF-8) — as an explicit step in `HashChainService.canonicalize()`, rather than relying on a JSON library's default serialization.

**Alternatives considered:**
- **Serialize with the default `ObjectMapper` (`objectMapper.writeValueAsString(payload)`) directly.** The most immediately obvious approach, and the one most likely to be suggested by an AI code-completion tool or a first-pass implementation. Rejected: default Jackson serialization does not guarantee stable key ordering across versions/configurations, which means the exact same logical payload could hash differently on a different run or after a dependency bump — silently breaking hash reproducibility, which undermines the entire tamper-evidence guarantee. This is flagged here explicitly because it is a known trap worth watching for once implementation (and AI-assisted code generation) begins — see `AI_USAGE_LOG.md`.

**Trade-offs / consequences:** A pipe-delimited or similarly simple canonical string format has a known limitation — a value containing the delimiter character isn't escaped by a naive scheme. This will be handled explicitly at implementation time (either an escaping rule or a length-prefixed encoding) and documented as a closed or open gap once implemented; noted here as a design-level risk to carry forward, not to invent a resolution to prematurely.

---

## ADR-007 — Assignment PDF confidentiality handling

**Status:** Proposed — awaiting explicit confirmation

**Context:** The assignment PDF is marked "Charles Schwab & Co., Inc. — Confidential & Proprietary... do not copy, distribute, re-host, or retain after submission." The repository will be shared with the review panel and, per the assignment's own submission instructions, reflects real development history rather than being deleted after submission.

**Decision (proposed):** Do not commit the raw PDF into the git repository. Keep it locally, outside version control (`.gitignore`), and commit only the engineer's own derived requirements documentation (`REQUIREMENTS.md`, this file, etc.) instead.

**Alternatives considered:**
- **Commit the PDF into the private repo** on the reasoning that the panel already has it and the repo is private. Not chosen as the default: "retain after submission" reads as a constraint on the artifact's lifecycle independent of who can see it, and avoiding the question entirely (by not committing it) is a strictly safer reading with no real cost, since none of the graded deliverables require the raw PDF to be present in the repo.

**Trade-offs / consequences:** None of substance — the derived documentation set is what the assignment actually asks to be produced and reviewed.

---

## ADR-008 — Authentication/authorization scope

**Status:** Accepted (2026-08-20, implemented in Milestone 7)

**Context:** The assignment does not mandate authentication as a functional requirement, but scores "security and production readiness" (assignment §8) and asks for "secure AI usage" and engineer ownership of production readiness (`NFR7`). A real audit system should not allow an anonymous caller to self-assert `actorId`. The previous evaluation's `SEC-02` finding (`docs/EVALUATION_CLOSURE_MATRIX.md` item 16) explicitly expects "incorrect issuer/audience" to be tested, which only makes sense for a JWT-based scheme, not a bare API key -- this superseded the originally proposed minimal-API-key default.

**Decision:** HMAC-signed JWTs (HS256, via the `io.jsonwebtoken`/jjwt library), carrying `sub` (subjectId), a custom `tenantId` claim, and a custom `roles` claim, with `iss`/`aud` validated on every request. A servlet filter (`JwtAuthenticationFilter`, not Spring Security) validates the token and populates a per-request `AuditSecurityContext` before any controller/service runs; requests without a valid token get 401 before reaching application logic. Token issuance is via `POST /dev/auth/token` -- **explicitly and repeatedly documented as not a real authentication endpoint**: it accepts any requested subjectId/tenantId/roles with zero identity verification. It exists only because this environment has no real OIDC identity provider to validate against, and the system needs to be demoable/testable end-to-end. A real deployment would replace this with JWKS-based verification against a real IdP (Okta, Auth0, Keycloak, etc.) and delete this endpoint entirely -- see `docs/SECURITY.md`.

**Why a custom filter instead of `spring-boot-starter-security`:** adding the full Spring Security starter changes a lot of default behavior at once (auto-secures all endpoints, pulls in a large dependency surface, requires learning/configuring its filter chain correctly under time pressure) in ways that are easy to get subtly wrong without thorough testing. A single `OncePerRequestFilter` doing exactly one job (validate JWT, populate context, reject otherwise) is small enough to read end-to-end and was fully testable within the time available. This is a legitimate trade-off, not a shortcut hidden from the reviewer: a real production system would likely prefer Spring Security (or an API gateway) for its maturity, extension points, and ecosystem familiarity.

**Alternatives considered:**
- **No authentication at all**, documented purely as a gap. Rejected once `SEC-02`/`SEC-03` made authentication and its negative-test coverage an explicit, testable requirement rather than a nice-to-have.
- **Minimal API-key check** (the originally proposed default). Rejected: doesn't support the tenant/role claims `SEC-03`'s authorization model needs, and can't meaningfully satisfy `SEC-02`'s issuer/audience expectation.
- **Full Spring Security + OAuth2 Resource Server** (JWKS-based, pointed at a real IdP). This is genuinely the more production-correct answer and is recorded here as the stated direction for a real deployment; not implemented because there is no real IdP available in this environment to validate against, and building a mock JWKS server to stand in for one was judged lower-value than the time spent on Scenario B/C.

**Trade-offs / consequences:** `POST /dev/auth/token` is a real, load-bearing security gap if this code were ever deployed as-is -- it must be removed (or hard-gated behind a build profile that never ships) before any real deployment. This is stated as plainly and as many times as reasonable across the docs specifically because it is the kind of thing that's easy to forget once the demo works.

---

## ADR-009 — Build tooling and language version

**Status:** Accepted (2026-08-20)

**Context:** A build tool and JDK version need to be picked before scaffolding.

**Decision:** Maven, Java 21 (LTS), Spring Boot 3.x.

**Alternatives considered:**
- **Gradle.** Equally valid; Maven chosen only because it is the more common default reviewers expect, with no functional advantage either way for a project this size.

**Trade-offs / consequences:** None material.

---

## ADR-010 — No update/delete endpoints, enforced by test

**Status:** Accepted (2026-08-20)

**Context:** `FR-A1` requires that records are append-only and that no update/delete operation is exposed anywhere in the API. This is easy to satisfy today and easy to accidentally violate later (e.g., a future PATCH endpoint added for an unrelated reason).

**Decision:** Enforce this by omission (no such controller method is ever written) *and* by an automated test that fails the build if any mutation mapping (`PUT`/`PATCH`/`DELETE`) is ever introduced on the audit endpoints, so the guarantee is checked mechanically rather than relying on it being remembered.

**Alternatives considered:**
- **Rely on omission alone, documented as a convention.** Simpler, but silently erodes over time as the codebase changes — nothing would catch a future regression.

**Trade-offs / consequences:** One additional test to write and maintain; in exchange, a core guarantee of the system becomes a build-breaking regression rather than a documentation-only promise. Enforced by `com.auditlog.controller.AppendOnlyApiTest`, implemented in Phase 1 and passing.

**Status update (Phase 1 implementation, 2026-08-20):** Implemented and tested. `AppendOnlyApiTest` reflects over `AuditEventController` and `AuditVerifyController` and asserts no method carries `@PutMapping`/`@PatchMapping`/`@DeleteMapping`.

---

## ADR-011 — Concurrency control for sequence/hash assignment

**Status:** Accepted (2026-08-20, implemented and tested in Phase 1)

**Context:** `docs/ARCHITECTURE.md` flags a concurrency risk: two writers appending at the same time must not be able to claim the same `sequence_no`, or link `previous_hash` to a value that is no longer actually the chain's current head by the time they commit. A naive `SELECT MAX(sequence_no)` followed by an `INSERT` is unsafe under concurrency — two transactions can both read the same max value before either commits.

**Decision:** A single-row `chain_head` table (seeded once, `id = 1`) holds the current `last_sequence_no` and `last_record_hash`. `AuditEventService.append()` takes a pessimistic row lock on this row (`SELECT ... FOR UPDATE`, via Spring Data's `@Lock(LockModeType.PESSIMISTIC_WRITE)` on `ChainHeadRepository.lockHead()`) at the start of its `@Transactional` method. A second concurrent writer's lock attempt blocks until the first transaction commits (which both inserts the new `audit_record` row and advances `chain_head`), at which point the second writer proceeds from the now-current state. This serializes all writes to the chain by design.

**Alternatives considered:**
- **Optimistic concurrency (a version/uniqueness check on `sequence_no`, retry on conflict).** Avoids holding a lock for the duration of a transaction, potentially better throughput under low contention. Rejected for this system: a hash chain write is inherently a serial operation (each record depends on the previous one's hash), so there is no real parallelism to preserve — optimistic retry would only add complexity (retry loops, backoff) to reach the same effectively-serial outcome, with an added risk of livelock under sustained contention.
- **In-memory JVM lock (e.g., a `synchronized` block or `ReentrantLock` in the service layer).** Simple and fast for a single instance, but does not generalize: multiple application instances (a real production deployment) would each have their own independent lock, doing nothing to prevent two different instances from racing against the same database. A database-level lock is the mechanism that actually generalizes to that case (see also `docs/EVALUATION_CLOSURE_MATRIX.md` item 12, which explicitly scopes "multi-instance" testing to a documented-reasoning limitation for this same reason — Phase 1 only tests concurrent threads within one instance).

**Trade-offs / consequences:** All writes are serialized through a single lock, which is a genuine write-throughput ceiling under heavy concurrent load — acceptable and arguably correct for an audit log, where correctness of the chain matters far more than write parallelism, and is explicitly documented as a scoped trade-off rather than a performance target. Validated by `ConcurrentAppendTest`: 20 concurrent writers produce a chain with 20 contiguous, uniquely-sequenced, correctly-linked records (`chainIntact: true`).

---

## ADR-012 — Tenant/resource authorization model

**Status:** Accepted (2026-08-20, implemented in Milestone 7)

**Context:** `docs/EVALUATION_CLOSURE_MATRIX.md` item 3 (`SEC-03`, P0) requires tenant isolation and resource-ownership authorization across query, redaction (not yet built), export (not yet built), and compliance (not yet built) endpoints, with BOLA/IDOR prevention and no cross-tenant data leakage. The assignment's original Scenario A design has no concept of "tenant" at all -- this is purely a requirement introduced by the evaluation, reflecting the reality that a real audit log service serves multiple clients/tenants on one shared chain.

**Decision:** Every `audit_record` row carries a `tenant_id` column (added via `V2__add_tenant_id.sql`), which is **part of the record's hashed content** (`HashChainService`) so it cannot be silently reassigned without breaking the chain, exactly like any other field. `tenant_id` is derived only from the authenticated principal's JWT `tenantId` claim on write -- never from the request body, so there is no field a caller could even attempt to override. On read (`GET /audit/events`, `GET /audit/events/{id}`), a caller without the `AUDITOR` role is always scoped to their own tenant regardless of what they pass; a cross-tenant `findById` returns 404 (not 403), since confirming another tenant's record exists is itself a leak. `GET /audit/verify` requires the `AUDITOR` role outright, since it is deliberately **not** tenant-scoped (see below).

**Why `/audit/verify` is not tenant-scoped:** the hash chain is one global, shared sequence across all tenants (`ADR-011`'s `chain_head` is a single row, not one per tenant). If verification were scoped to a single tenant's sub-sequence of records, records belonging to *other* tenants interleaved in the real sequence would look like sequence gaps -- a false `MISSING_RECORD` violation with nothing actually wrong. Gating the whole endpoint behind `AUDITOR` avoids this false-positive entirely rather than trying to make tenant-scoped verification "work" around it.

**Alternatives considered:**
- **Separate per-tenant hash chains** (each tenant gets its own `sequence_no`/`chain_head`). Would allow a genuinely tenant-scoped `/audit/verify` with no false-positive risk. Rejected for this time-box: it multiplies the concurrency-control mechanism (`ADR-011`) per tenant, complicates the export bundle's chain-context fields (Scenario B, not yet built), and the assignment's core design already assumes one chain. Worth revisiting if true multi-tenant chain isolation becomes a hard requirement later.
- **Row-level security enforced at the database layer** (e.g., Postgres RLS policies keyed on a session variable). More defense-in-depth than an application-layer filter alone. Not pursued: H2 (`ADR-001`) has materially weaker support for this than Postgres, and it would only be meaningfully testable after revisiting the H2 decision.
- **403 instead of 404 for cross-tenant `findById`.** Rejected: a 403 confirms the record exists, which is exactly the kind of cross-tenant information leak `SEC-03` and the assignment's Section 12 explicitly call out to prevent.

**Trade-offs / consequences:** the `ResourceAuthorization` interface introduced in Phase 1 (`com.auditlog.security`) remains unimplemented and unused -- tenant scoping was implemented directly in `AuditQueryService`/`AuditEventService`/`ChainVerificationService` rather than through that interface, since tenant identity match is a simpler and more direct check than the generic resource-ACL shape that interface was designed for. It remains a documented extension point for a future finer-grained (non-tenant) resource authorization need, not dead code to be deleted -- but it is not what closes `SEC-03`. Validated by `TenantIsolationTest` (7 tests) and `SecurityAuthenticationTest`/`JwtServiceTest` (12 tests combined) plus a live manual run (two tenants, cross-tenant query returns empty, cross-tenant fetch returns 404, `/audit/verify` returns 403 for `ROLE_USER` and 200 for `ROLE_AUDITOR`).
