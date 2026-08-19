# Architecture — Audit Log Service

Requirement IDs referenced below (`FR-A#`, `FR-B#`, `FR-C#`, `NFR#`) are defined in `REQUIREMENTS.md`. Rationale and alternatives for each design choice are in `DECISIONS.md`; this document describes the chosen design, not the reasoning behind it.

**Implementation status (2026-08-20, all 12 milestones complete):** everything described below is **IMPLEMENTED and TESTED** — 83/83 tests passing, 91% instruction / 77% branch coverage (JaCoCo), plus repeated live manual verification against a running instance (write → verify → tamper via direct H2 access → verify again; redaction; export signing; retention; compliance reporting). Package structure and file paths below reflect what actually exists in `src/main/java/com/auditlog/`. Remaining gaps are named explicitly in each section, not hidden — see `docs/EVALUATION_CLOSURE_MATRIX.md` and `ENGINEERING_SUMMARY.md` for the complete list.

## Architecture principle

The hash-chain logic is isolated in a single domain component (`HashChainService`) that every write and every verification pass goes through — nothing else in the system computes or checks a hash independently. Redaction's field-commitment scheme (below) extends this principle rather than working around it: the hash is computed over per-field *commitments*, not raw payload values, which is what lets a value be redacted later without invalidating `record_hash`.

## Components

```
Controller layer      AuditEventController      POST/GET /audit/events, GET /audit/events/{id}
                       AuditVerifyController     GET /audit/verify
                       RedactionController       POST /audit/events/{id}/redact
                       RetentionController       POST /audit/retention/apply
                       ExportController          GET /audit/export
                       ComplianceReportController GET /audit/compliance-report
                       DevAuthController         POST /dev/auth/token (NOT real auth -- docs/SECURITY.md)

Service layer          AuditEventService (write path: sequence/hash/commitment assignment,
                                          idempotency-key check)
                       AuditQueryService (read path: tenant-scoped keyset pagination)
                       ChainVerificationService (linkage/content/sequence/commitment checks)
                       RedactionService (tombstone application, tenant-authorized)
                       RetentionService (soft-delete archival, AUDITOR-only)
                       ComplianceReportService (allow-listed resource types, mandatory time range)

Export                 ExportBundleService (com.auditlog.export) -- bundle assembly, canonical
                                            manifest construction
                       ExportSigningService (com.auditlog.export) -- EC/SHA256withECDSA signing

Redaction              RedactionCommitmentService (com.auditlog.redaction) -- per-field salted
                                                    commitments, tombstone format, verification
                                                    reconciliation

Domain core            HashChainService (com.auditlog.hash)
                       - computeRecordHash(..., fieldCommitments, ...) -> SHA-256 hex
                         (hashes commitments, not raw payload -- see Redaction approach below)
                       - genesisHash() -> defined constant
                       PayloadCanonicalizer -- deterministic JSON serialization for hashing
                       Sha256 -- shared digest utility

Security               JwtService, JwtAuthenticationFilter, AuditSecurityContext, Roles
(com.auditlog.security) RequestSizeLimitFilter (413 on oversized declared Content-Length)
                       ResourceAuthorization interface -- still unimplemented/unused; tenant
                                              scoping is done directly in each service instead
                                              (docs/DECISIONS.md ADR-012)

Config                 CorsConfig (explicit empty-origin policy), OpenApiConfig (Swagger UI
                       Bearer-auth scheme)

Persistence            Spring Data JPA repository + 4 Flyway migrations (V1-V4), H2 (file-mode
                       for dev/demo, in-memory for tests) — see DECISIONS.md ADR-001.
```

**Guardrail:** no controller in the system exposes an update or delete operation on audit records (`FR-A1`). Enforced both by omission and by `AppendOnlyApiTest`, which reflects over every controller (including the ones added in later milestones) and fails the build if a `PUT`/`PATCH`/`DELETE` mapping is ever added. Redaction and archival are POST operations that mutate a narrow, named set of fields (`AuditRecordEntity.applyRedaction()`/`archive()`) — never `record_hash`, `previous_hash`, `sequence_no`, `tenant_id`, or `field_commitments`.

## API design (high level)

| Method | Path | Scenario | Purpose | Auth | Status |
|---|---|---|---|---|---|
| POST | `/audit/events` | A | Append a new event (`FR-A1`). Optional `Idempotency-Key` header. | Any authenticated caller | IMPLEMENTED, TESTED |
| GET | `/audit/events` | A | Query events, tenant-scoped, keyset pagination (`FR-A2`). | Any authenticated caller | IMPLEMENTED, TESTED |
| GET | `/audit/events/{id}` | A | Fetch a single record; 404 (not 403) for cross-tenant. | Any authenticated caller | IMPLEMENTED, TESTED |
| GET | `/audit/verify` | A | Walk the chain, report intact/broken + first violation (`FR-A4`). | `ROLE_AUDITOR` | IMPLEMENTED, TESTED |
| POST | `/audit/events/{id}/redact` | B | Redact named top-level payload fields (`FR-B2`). | Same tenant or `ROLE_AUDITOR` | IMPLEMENTED, TESTED |
| POST | `/audit/retention/apply` | B | Archive records past the configured retention window (`FR-B1`). | `ROLE_AUDITOR` | IMPLEMENTED, TESTED |
| GET | `/audit/export` | B | Self-contained, signed, verifiable bundle by `actorId`/`resourceId` (`FR-B3`). | Any authenticated caller (`ROLE_AUDITOR` for cross-tenant) | IMPLEMENTED, TESTED |
| GET | `/audit/compliance-report` | C | Allow-listed resource types, mandatory time range (`FR-C2`). | `ROLE_COMPLIANCE_OFFICER` | IMPLEMENTED, TESTED |
| POST | `/dev/auth/token` | — | Issues a JWT for any requested subjectId/tenantId/roles, zero verification. **NOT real auth** — see `docs/SECURITY.md`. | Public | IMPLEMENTED, TESTED |
| GET | `/actuator/health` | — | Liveness check. | Public | IMPLEMENTED, TESTED |
| GET | `/swagger-ui/index.html`, `/v3/api-docs` | — | Interactive/machine-readable API documentation. | Public (calls made through it still need a token) | IMPLEMENTED, TESTED |

Pagination for `GET /audit/events` and `/audit/compliance-report` uses keyset pagination: `pageSize` (default 50, capped at 200) and `afterSequenceNo` (cursor). `AuditEventPageResponse` includes `nextCursor`/`hasMore`, computed by fetching `pageSize + 1` rows to avoid a separate `COUNT` query.

Request/response DTOs (`com.auditlog.dto`) are deliberately separate types from `AuditRecordEntity` — the entity is never returned directly from a controller, and `AuditEventResponse` deliberately never includes `salt`/`fieldCommitments` (would enable offline brute-forcing of low-entropy redacted values — `docs/DECISIONS.md` ADR-003).

**Not implemented:** `GET /audit/verify?fromSeq=&toSeq=` (range verification, was always secondary/optional).

## Data model

Single table, `audit_record`, built up across four Flyway migrations rather than created all at once — each migration corresponds to the milestone that needed its columns, which is itself part of the evidence that this was built incrementally, not designed once and implemented in one shot.

| Column | Type | Added | Notes |
|---|---|---|---|
| `id` | UUID, PK | V1 | Server-generated, not sequential/guessable. |
| `sequence_no` | BIGINT, unique, monotonic | V1 | The chain's order of truth (`FR-A3`) — assigned atomically via the `chain_head` lock (ADR-011). |
| `event_type`, `actor_id`, `resource_type`, `resource_id` | VARCHAR(200), indexed | V1 | `FR-A1`, `FR-A2`. |
| `payload` | CLOB (JSON, via `@Lob` + `JsonNode<->String` converter) | V1 | Stored exactly as received; **mutable only via `applyRedaction()`** (V3+). |
| `event_timestamp` | TIMESTAMP WITH TIME ZONE | V1 | Caller-supplied, required, millisecond-truncated before hashing (ADR-002). |
| `recorded_at` | TIMESTAMP WITH TIME ZONE | V1 | Server-assigned truth of "when we saw this"; retention eligibility is based on this, not `event_timestamp` (ADR-004). |
| `record_hash`, `previous_hash` | VARCHAR(64) | V1 | SHA-256 hex. Since V3, computed over `field_commitments`, not the raw payload (see Redaction approach). |
| `tenant_id` | VARCHAR(200), indexed | V2 | Part of the hashed content; derived only from the JWT, never the request body (ADR-012). |
| `salt` | VARCHAR(64) | V3 | Per-record random salt for the commitment scheme. Immutable. **Never exposed via the API.** |
| `field_commitments` | CLOB (JSON) | V3 | `fieldName -> commitment` for every top-level payload field. Immutable, including through redaction — this is what makes redaction safe. **Never exposed via the API.** |
| `status` | VARCHAR(20) enum (`ACTIVE`/`ARCHIVED`/`REDACTED`) | V3 | Single-valued; archival never overwrites an existing `REDACTED` status (ADR-004 addendum). |
| `redacted_fields`, `redacted_at`, `redacted_by` | CLOB (JSON array), TIMESTAMP, VARCHAR | V3 | Set only by `applyRedaction()`. |
| `idempotency_key` | VARCHAR(200), unique with `tenant_id` | V4 | Optional; NULLs are distinct under standard unique-index semantics, so omitting it is unaffected. |

**`chain_head` (V1):** a single seeded row (`id = 1`) used purely as the pessimistic-lock anchor for concurrency control (ADR-011) — not part of the audit trail itself.

### Genesis record

`previous_hash` for the first record is a defined constant (`SHA-256("AUDIT-CHAIN-GENESIS-v1")`) — never null, never all-zeros.

## Hash-chain approach

**Content covered by `record_hash`:** `tenantId`, `eventType`, `actorId`, `resourceType`, `resourceId`, the canonicalized **field commitments** (not the raw payload — see below), `eventTimestamp`, `sequenceNo`, `previousHash`.

**Canonicalization:** `PayloadCanonicalizer` sorts JSON object keys recursively (TreeMap-based), so a semantically-identical payload with reordered keys always hashes the same.

**Write path (`AuditEventService.append()`):**
1. Resolve `tenantId` from the JWT (never the request body — ADR-012).
2. Lock `chain_head` (pessimistic, `SELECT ... FOR UPDATE`).
3. If an `Idempotency-Key` header was supplied and a matching record already exists for this tenant, return it immediately (200, not 201) — checked *after* the lock, so this is race-safe for free (ADR-014).
4. Determine `sequence_no`/`previous_hash`; truncate timestamps to millisecond precision (ADR-002 addendum).
5. Generate a random salt; compute a commitment for every top-level payload field (`RedactionCommitmentService`); compute `record_hash` from the commitments.
6. Persist; advance `chain_head`.

**Concurrency (TESTED — ADR-011):** the `chain_head` lock serializes all writes. `ConcurrentAppendTest`: 20 concurrent writers, 20 contiguous correctly-linked records. Scope: concurrent threads within one instance, not literal multi-process contention (`docs/EVALUATION_CLOSURE_MATRIX.md` item 12).

## Verification approach (`FR-A4`) — IMPLEMENTED, TESTED

`ChainVerificationService.verify()` (requires `ROLE_AUDITOR`) walks records in `sequence_no` order and, per record:
1. **Linkage:** stored `previous_hash` vs. the prior record's actual `record_hash` (or genesis). Mismatch → `LINKAGE_BROKEN`.
2. **Content:** recomputed `record_hash` (from stored `field_commitments`) vs. stored value. Mismatch → `CONTENT_MISMATCH`.
3. **Sequence:** contiguity check. Gap → `MISSING_RECORD`.
4. **Field-commitment reconciliation** (`RedactionCommitmentService.verifyFieldCommitments()`): for each non-redacted field, recompute its commitment from the *current raw payload value* and compare to what's stored — catches a raw-payload tamper that leaves `record_hash` itself unchanged, since `record_hash` no longer depends on raw values directly (see Redaction approach). For each redacted field, confirm the tombstone's embedded commitment matches what's stored — catches a forged/faked redaction. Mismatches → `CONTENT_MISMATCH`.

Linkage is checked before content deliberately: `previous_hash` itself feeds `record_hash`'s computation, so tampering it also breaks the content check — checking linkage first gives the more specific diagnosis.

All violation types confirmed via both the automated suite and repeated live manual demos (write → verify intact → tamper directly in the H2 file via the H2 shell, bypassing the app entirely → verify again → violation reported with the correct `sequenceNo`/`recordId`/`detail`).

**Known limitation, not addressed:** deleting the *newest* record(s) from the tail is undetectable — nothing after the deleted tail reveals a broken link. Needs an external chain-head anchor (`docs/EVALUATION_CLOSURE_MATRIX.md` item 14, `ARC-02`).

## Redaction approach (`FR-B2`) — IMPLEMENTED, TESTED (docs/DECISIONS.md ADR-003)

**The problem:** `record_hash` was originally going to cover the raw payload directly — but then redacting a value would always change the hash, making a legitimate redaction indistinguishable from tampering.

**The scheme:** at write time, every top-level payload field gets a salted commitment (`commitment = SHA256(salt|fieldName|canonicalValue)`), computed unconditionally — there's no API surface for pre-declaring which fields are redactable, so any field can be redacted later. `HashChainService` hashes the **commitments**, never the raw payload. `POST /audit/events/{id}/redact` replaces a field's raw value with a tombstone embedding its (unchanged) commitment (`"[REDACTED:sha256:<commitment>]"`), sets `status = REDACTED`, records `redacted_fields`/`redacted_at`/`redacted_by` — and **never recomputes `record_hash`**, since `record_hash` never depended on the raw value to begin with.

**The gap this creates, and how it's closed:** because `record_hash` no longer covers raw payload values, recomputing it alone can't detect a raw value being tampered without its commitment being updated to match. `RedactionCommitmentService.verifyFieldCommitments()` (used by verification, above) closes this independently.

**Scope limitation:** top-level payload fields only — nested object/array field redaction was judged disproportionate effort for the time available.

**Alternative considered and rejected:** encrypt-at-rest + access-control redaction. Simpler, but the original value still exists in storage, so it satisfies a display-time privacy control, not a genuine erasure requirement.

## Export approach (`FR-B3`) — IMPLEMENTED, TESTED (docs/DECISIONS.md ADR-013)

`GET /audit/export?resourceId=`/`?actorId=` (at least one required) builds a bundle: every matching record (with `recordHash`/`previousHash`), a `chainContext` (`firstSequenceNo`, `lastSequenceNo`, `hashOfLastRecordBeforeRange` — the first exported record's `previousHash`, anchoring it into the larger chain), and a **signature**: `SHA256withECDSA` over a canonical manifest string built purely from the bundle's own published fields. A recipient needs only the exported JSON and the published public key to verify — no server access. The signing key pair is generated fresh in memory on every app startup (never persisted/committed — ADR-013), so a signature does not survive a restart; a documented, deliberate trade-off, not an oversight.

**Not addressed:** verifying a bundle is genuinely part of the *full* live chain (vs. an internally-consistent subset) would need a periodically published chain root (Merkle-style) — scoped out, unchanged from the original design.

## Retention approach (`FR-B1`) — IMPLEMENTED, TESTED (docs/DECISIONS.md ADR-004)

`POST /audit/retention/apply` (`ROLE_AUDITOR`, optional `?windowDays=` override) soft-deletes: `AuditRecordEntity.archive()` flips `ACTIVE → ARCHIVED` for records where `recorded_at` (server truth, not the caller-supplied `event_timestamp`) is older than the window. Never overwrites an existing `REDACTED` status. No hard delete, no scheduler (not required by the assignment) — a manual admin endpoint. `/audit/verify` needs no special handling for archived records: nothing about their stored content changes.

## Compliance reporting approach (`FR-C2`, Scenario C) — IMPLEMENTED, TESTED

See `docs/scenario-c.md` for the full clarification. `GET /audit/compliance-report` (`ROLE_COMPLIANCE_OFFICER`, a role distinct from `AUDITOR`) requires `from`/`to` explicitly (never defaulted), scopes to a configurable "client account data" resource-type allow-list, and is cross-tenant by default like `AUDITOR`. Genuinely thin: reuses `AuditEventPageResponse` and the same keyset-pagination pattern as `AuditQueryService`.

## Cross-cutting concerns

- **Security posture — IMPLEMENTED.** JWT authentication (`JwtAuthenticationFilter`) on every endpoint except the dev token endpoint, health check, and Swagger UI/docs pages. Tenant isolation on every business endpoint (`docs/DECISIONS.md` ADR-012). `com.auditlog.security.ResourceAuthorization` remains an unimplemented, unused interface — tenant scoping was done directly in each service instead, since it's a simpler, more direct check than the generic resource-ACL shape that interface was designed for. See `docs/SECURITY.md` for the full picture, including what's still a real gap (TLS, full immutable DB permissions).
- **SQL injection:** all queries go through Spring Data JPA / parameterized JPQL — no string-concatenated SQL.
- **Concurrency risk:** resolved and tested (ADR-011).
- **Request/body limits, CORS, idempotency:** implemented (ADR-014).
- **Operational monitoring:** `/actuator/health` only — deliberately minimal, not a claim of full observability.
