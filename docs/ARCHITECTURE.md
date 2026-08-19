# Architecture — Audit Log Service

Requirement IDs referenced below (`FR-A#`, `FR-B#`, `FR-C#`, `NFR#`) are defined in `REQUIREMENTS.md`. Rationale and alternatives for each design choice are in `DECISIONS.md`; this document describes the chosen design, not the reasoning behind it.

**Implementation status (2026-08-20, Phase 1 — Project Foundation + Scenario A Core Domain):** everything under "Scenario A" below (write API, query API, hash chain, chain verification, concurrency control) is **IMPLEMENTED and TESTED** — 24/24 tests passing, including a live manual tamper demo against a running instance (see `docs/TESTING.md`). Everything under "Scenario B" (retention, redaction, export) is **design-only, NOT IMPLEMENTED** — Phase 1 was explicitly scoped to Scenario A. Scenario C is likewise not started. Package structure and file paths below reflect what actually exists in `src/main/java/com/auditlog/`, not just a proposal.

## Architecture principle

The hash-chain logic is isolated in a single domain component that every write and every verification pass goes through — nothing else in the system computes or checks a hash independently. This is deliberate: the hash chain is the crux of the assignment, and having one well-tested implementation is safer than re-deriving the logic in multiple places.

## Components

```
Controller layer     AuditEventController, AuditVerifyController        [IMPLEMENTED]
                      ExportController, ComplianceReportController      [NOT IMPLEMENTED -- Scenario B/C]

Service layer         AuditEventService (write path)                    [IMPLEMENTED]
                      AuditQueryService (read path)                     [IMPLEMENTED]
                      RetentionService                                  [NOT IMPLEMENTED -- Scenario B]

Verification layer    ChainVerificationService                          [IMPLEMENTED]
                      RedactionService                                  [NOT IMPLEMENTED -- Scenario B]
                      ExportBundleService                                [NOT IMPLEMENTED -- Scenario B]

Domain core            HashChainService (com.auditlog.hash)              [IMPLEMENTED]
                      - PayloadCanonicalizer.canonicalize(payload) -> canonical string
                      - computeRecordHash(...) -> SHA-256 hex
                      - genesisHash() -> defined constant
                      Every write and every verify operation calls into this component;
                      no other component computes a hash independently.

Security foundation    com.auditlog.security.AuthenticatedPrincipal,     [FORWARD-DECLARED ONLY --
                      ResourceAuthorization (interface, no impl)          not wired into any request path]

Persistence            Spring Data JPA repository + Flyway migration     [IMPLEMENTED]
                      (V1__init_schema.sql), H2 (file-mode for dev/demo,
                      in-memory for tests) — see DECISIONS.md ADR-001.
```

**Guardrail:** no controller in the system exposes an update or delete operation on audit records (`FR-A1`). This is enforced both by omission (no such endpoint is written) and by an automated test asserting no such mapping exists, so the guarantee survives future changes rather than depending on a convention being remembered.

## API design (high level)

| Method | Path | Scenario | Purpose | Status |
|---|---|---|---|---|
| POST | `/audit/events` | A | Append a new event (`FR-A1`). Only write operation exposed. | IMPLEMENTED, TESTED |
| GET | `/audit/events` | A | Query events with filters + keyset pagination (`FR-A2`). | IMPLEMENTED, TESTED |
| GET | `/audit/events/{id}` | A | Fetch a single record. | IMPLEMENTED |
| GET | `/audit/verify` | A | Walk the chain, report intact/broken + first violation (`FR-A4`). | IMPLEMENTED, TESTED |
| GET | `/audit/verify?fromSeq=&toSeq=` | A | Range verification (secondary, only if time allows). | NOT IMPLEMENTED |
| (admin/scheduled trigger) | Archival operation | B | Transition eligible records to `ARCHIVED` per retention policy (`FR-B1`). | NOT IMPLEMENTED |
| POST | `/audit/events/{id}/redact` (path indicative) | B | Redact flagged fields in a record's payload (`FR-B2`). | NOT IMPLEMENTED |
| GET | `/audit/export?resourceId=` / `?actorId=` | B | Export a self-contained, verifiable bundle (`FR-B3`). | NOT IMPLEMENTED |
| GET | `/audit/compliance-report` (path indicative) | C | Thin reporting layer over the query/export services, scoped per `docs/scenario-c.md` (`FR-C2`). | NOT IMPLEMENTED |

Pagination for `GET /audit/events` uses keyset pagination: `pageSize` (default 50, capped at 200 server-side regardless of what's requested) and `afterSequenceNo` (cursor) query parameters. The response includes `nextCursor` and `hasMore`. Implemented in `AuditQueryService` by fetching `pageSize + 1` rows to detect `hasMore` without a separate `COUNT` query.

Request/response DTOs (`com.auditlog.dto`) are deliberately separate types from `AuditRecordEntity` — the entity is never returned directly from a controller.

## Data model

Single table, `audit_record`, is sufficient for this prototype — there is no requirement driving a multi-table design, and one table keeps the hash-chain invariant (each row's hash covers a fixed, well-understood set of columns) easy to reason about.

**Phase 1 note:** `V1__init_schema.sql` creates only the columns Scenario A actually needs (through `previous_hash` below), plus the `chain_head` singleton table used for concurrency control (`DECISIONS.md` ADR-011). The `status`, `redacted_fields`, `field_commitments`, and `salt` columns are **not yet created** — they are Scenario B's design (still accurate as a forward design below) and will arrive in a later Flyway migration when that work starts, rather than being added speculatively now.

| Column | Type (as implemented) | Notes |
|---|---|---|
| `id` | UUID, PK | Server-generated. Not sequential/guessable, so it can't be used to infer record count or chain position. |
| `sequence_no` | BIGINT, unique, monotonic | The chain's order of truth (`FR-A3`) — assigned atomically at insert via the `chain_head` lock (ADR-011). See `DECISIONS.md` ADR-002 for why this, not `event_timestamp`, governs ordering. |
| `event_type` | VARCHAR(200) | e.g. `USER_LOGIN` (`FR-A1`). |
| `actor_id` | VARCHAR(200), indexed | `FR-A1`, `FR-A2`. |
| `resource_type` | VARCHAR(200), indexed | `FR-A1`, `FR-A2`. |
| `resource_id` | VARCHAR(200), indexed (composite with `resource_type`) | `FR-A1`, `FR-A2`. |
| `payload` | CLOB (JSON text, via `@Lob` + a `JsonNode<->String` `AttributeConverter`) | Structured event detail (`FR-A1`), stored exactly as received -- canonicalization happens only in-memory when hashing, never to what's persisted. |
| `event_timestamp` | TIMESTAMP WITH TIME ZONE | Caller-supplied, **required**, truncated to millisecond precision before hashing/persisting (`FR-A1`; see ADR-002's implementation addendum). |
| `recorded_at` | TIMESTAMP WITH TIME ZONE | Server-assigned, always, same millisecond truncation — the audit truth of "when the service saw this." |
| `record_hash` | VARCHAR(64) | SHA-256 hex of the record's canonical content (`FR-A3`). (Implemented as `VARCHAR` rather than `CHAR` -- see `DECISIONS.md` ADR-001 area for the Hibernate schema-validation reason; functionally identical for a fixed-length hex string.) |
| `previous_hash` | VARCHAR(64) | Hash of the prior record, or the genesis constant for the first record (`FR-A3`). |

Not yet implemented (Scenario B design, unchanged from the original proposal):

| Column | Type | Notes |
|---|---|---|
| `status` | ENUM(`ACTIVE`, `ARCHIVED`, `REDACTED`) | `FR-B1`, `FR-B2`. |
| `redacted_fields` | JSON, nullable | Metadata about what was redacted, when, and why (`FR-B2`). |
| `field_commitments` | JSON, nullable | Per-field salted hash for fields flagged as redactable, computed at write time (`FR-B2`; see below). |
| `salt` | VARCHAR, nullable | Per-record random salt used in the commitment scheme. |

**`chain_head` (implemented, Phase 1):** a single seeded row (`id = 1`, `last_sequence_no`, `last_record_hash`) used purely as a pessimistic-lock anchor for concurrency control — not part of the audit trail itself. See `DECISIONS.md` ADR-011.

### Genesis record

`previous_hash` for the first record in the chain is a defined constant (e.g. `SHA-256("AUDIT-CHAIN-GENESIS-v1")`), documented in code — never null and never all-zeros, both of which would be ambiguous with "not yet computed."

## Hash-chain approach

**Content covered by `record_hash`:** a fixed, ordered set of fields — `eventType`, `actorId`, `resourceType`, `resourceId`, the canonicalized `payload`, `eventTimestamp`, `sequenceNo`, and `previousHash`. Mutable bookkeeping fields (`id`, `status`, `redacted_fields`) are deliberately excluded, since they need to change later (archival, redaction) without invalidating the hash.

**Canonicalization:** the payload is a structured JSON object, and hashing requires a single deterministic byte representation of it — object key order, number formatting, and encoding must be fixed, not left to whatever a JSON library happens to produce on a given run. The design commits to a canonical serialization step (sorted keys, fixed encoding) as part of `HashChainService.canonicalize()`, applied before hashing, and covered directly by unit tests that check hash stability across differently-ordered-but-equivalent payload inputs.

**Write path (as implemented, `AuditEventService.append()`):**
1. Take a pessimistic lock on the `chain_head` row (see Concurrency below).
2. Determine the next `sequence_no` and the `previous_hash` (the locked head's `last_record_hash`, or the genesis hash if `last_sequence_no == 0`).
3. Truncate `event_timestamp`/`recorded_at` to millisecond precision (see `DECISIONS.md` ADR-002 addendum), canonicalize the payload, and compute `record_hash`.
4. Persist the new record; advance `chain_head` to the new `sequence_no`/`record_hash` within the same transaction.

**Concurrency (IMPLEMENTED, TESTED — see `DECISIONS.md` ADR-011):** a single-row `chain_head` table is locked via `SELECT ... FOR UPDATE` (Spring Data `@Lock(PESSIMISTIC_WRITE)`) for the duration of each write transaction, serializing sequence/hash assignment across concurrent writers. Validated by `ConcurrentAppendTest`: 20 concurrent writers produce a chain with 20 contiguous, correctly-linked records. Scope note: this tests concurrent threads within one application instance, not literal multi-process contention — see `docs/EVALUATION_CLOSURE_MATRIX.md` item 12.

## Verification approach (`FR-A4`) — IMPLEMENTED, TESTED

`ChainVerificationService.verify()` walks records in `sequence_no` order and, for each one, in this order:
1. Compares the stored `previous_hash` to the actual `record_hash` of the prior record in sequence (or the genesis hash, for the first record). A mismatch is a **`LINKAGE_BROKEN`** violation.
2. Recomputes `record_hash` from the record's stored, canonicalized content and compares it to the stored value. A mismatch is a **`CONTENT_MISMATCH`** violation.
3. Checks `sequence_no` is exactly one more than the previous record's. A gap is a **`MISSING_RECORD`** violation.

**Why linkage is checked before content:** `previous_hash` is itself one of the fields that feeds a record's own `record_hash` computation. Directly tampering `previous_hash` in the data store therefore *also* makes the content-hash recomputation fail (since the recomputed hash now uses the tampered `previous_hash` value as input, which no longer matches what was used to compute the originally-stored `record_hash`). Checking linkage first reports the more specific, structural diagnosis (`LINKAGE_BROKEN`) for that case, while a pure payload tamper (content changed, `previous_hash` untouched) still correctly surfaces as `CONTENT_MISMATCH`. This ordering was corrected during Phase 1 testing after `TamperDetectionTest.detectsIncorrectPreviousHash` initially reported `CONTENT_MISMATCH` instead of the expected `LINKAGE_BROKEN` — both were technically true simultaneously, but `LINKAGE_BROKEN` is the more actionable diagnosis for that specific tamper.

All three violation types were exercised and confirmed both via the automated integration suite and a live manual demo (write via API → verify intact → tamper directly in the H2 data store via the H2 shell, bypassing the application → verify again → `CONTENT_MISMATCH` reported with the correct `sequenceNo` and `recordId`). See `docs/TESTING.md`.

`ARCHIVED` (`FR-B1`) and `REDACTED` (`FR-B2`) record handling is design-only — not implemented in Phase 1, since no record can yet have either status.

**Known limitation (unchanged from original design, not addressed in Phase 1):** deleting the *newest* record(s) from the tail is not detectable — there is nothing after the deleted tail to reveal a broken link. This requires an external chain-head anchor; tracked as `docs/EVALUATION_CLOSURE_MATRIX.md` item 14 (`ARC-02`).

## Redaction approach (design level, `FR-B2`) — NOT IMPLEMENTED (Scenario B, not started)

**The problem:** `record_hash` is computed over the original payload. Naively removing or blanking a sensitive value after the fact changes the content the hash covers, so the record would appear tampered — the opposite of the intended outcome.

**Chosen approach — field-commitment scheme:**
1. At write time, for any payload field flagged as potentially redactable, compute a salted commitment: `commitment = SHA-256(salt + fieldValue)`. The salt is random per record. These commitments are included in the content that feeds `record_hash`, so they are covered by tamper-evidence from the moment the record is written — not bolted on later.
2. When a field is redacted: the sensitive value in `payload` is replaced with a tombstone marker that embeds the field's precomputed commitment (e.g. `"[REDACTED:sha256:<commitment>]"`); `status` becomes `REDACTED`; `redacted_fields` records which fields, when, and why. **`record_hash` is never recomputed** — it still reflects the original content's commitments, which is what makes this safe.
3. During verification, a `REDACTED` record is checked by confirming the current payload's tombstone markers match the stored `field_commitments`, and that the non-redacted fields together with the stored commitments (not the raw values) still reproduce `record_hash`. This is what lets a redacted record continue to pass `/audit/verify` while the underlying sensitive value is genuinely gone from the stored payload.

**Documented limitation:** only fields flagged as redactable at write time can later be redacted without breaking the chain — this is a deliberate constraint of the scheme, not an oversight, and follows directly from needing the commitment to exist before the hash is computed.

**Alternative considered and not chosen:** encrypting the value at rest and treating redaction as an access-control problem (hide the decrypted value from unauthorized viewers) rather than a data-mutation problem. Rejected as the primary approach because the original value still exists at rest — it does not satisfy a genuine erasure/privacy requirement, only a display-time one. Recorded as an alternative in `DECISIONS.md` (ADR-003) since it is materially simpler and may be the right trade-off under tighter time constraints.

## Cross-cutting concerns

- **Security posture — NOT IMPLEMENTED beyond forward-declared types.** `com.auditlog.security.AuthenticatedPrincipal` (record) and `ResourceAuthorization` (interface, no implementation) exist so later service signatures don't require a larger rewrite, but nothing in Phase 1 constructs or consults them — no authentication or authorization is enforced on any endpoint. See `docs/SECURITY.md` for the full, honest statement of this gap and `docs/EVALUATION_CLOSURE_MATRIX.md` items 3 and 16.
- **SQL injection:** all queries go through Spring Data JPA / parameterized queries — no string-concatenated SQL. Confirmed by inspection of `AuditRecordRepository` (JPQL with bound `:param` placeholders throughout).
- **Concurrency risk:** RESOLVED and TESTED — see Concurrency under Hash-chain approach above and `DECISIONS.md` ADR-011.
- **Bundle export integrity (`FR-B3`):** NOT IMPLEMENTED (Scenario B). Design unchanged from the original proposal: the export bundle would include each record's hash and linkage fields plus a bundle-level integrity value (e.g., a hash over the sorted set of included record hashes), letting a recipient verify internal consistency and that the bundle wasn't altered after export. Verifying that the bundle's contents are genuinely part of the *full* chain (not just internally consistent) would require either a periodically published chain root (Merkle-style) or trusting the exporting system — recorded as a scoped limitation for when this is built.
