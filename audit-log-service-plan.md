# Audit Log Service — Architecture & Build Plan (Java / Spring Boot)

## 0. Ground Rules Baked Into the Plan

- Every deliverable in §7 of the assignment maps to a folder/file below — nothing invented, nothing missing.
- The plan is split into **Scenario A → B → C**, matching how the assignment wants you to demonstrate decomposition.
- AI usage log is treated as a first-class artifact you update *as you go*, not reconstructed at the end.
- Commit early, commit often — the graders explicitly check for real history, not a single "initial commit".

---

## 1. Tech Stack

| Concern | Choice | Why |
|---|---|---|
| Language/Framework | Java 21 + Spring Boot 3.3.x | LTS Java, current Boot |
| Build | Maven | Most reviewers default to it; Gradle fine too |
| Persistence | PostgreSQL (prod profile) + H2 (test/dev profile) | Real DB for the "modify a record directly in the data store" tamper test — H2 alone makes that step awkward to demo |
| ORM | Spring Data JPA + Hibernate | Standard, fast to build |
| Migrations | Flyway | Versioned schema, looks production-grade to reviewers |
| API docs | springdoc-openapi (Swagger UI) | Free API documentation deliverable |
| Validation | Jakarta Bean Validation | Input constraints on the write API |
| Hashing | `java.security.MessageDigest` (SHA-256) | No extra dependency, defensible choice |
| JSON canonicalization | Jackson with a deterministic `ObjectMapper` config (sorted keys) | **Critical** — hash must be computed over a canonical byte representation, not whatever Jackson feels like that day |
| Testing | JUnit 5, Mockito, Spring Boot Test, Testcontainers (Postgres) | Testcontainers lets you test against real Postgres, incl. the tamper scenario |
| Security | Spring Security (basic API-key or JWT auth stub) | "Enforce secure AI usage" + "production readiness" scoring criteria expects *something* here even if scoped down |
| Logging AI usage | `AI_USAGE_LOG.md` + per-commit trailer | See §9 |

---

## 2. High-Level Architecture

```
┌─────────────────────────────────────────────────────────────┐
│                        REST API Layer                        │
│  AuditEventController   AuditVerifyController   ExportCtrl   │
└───────────────┬───────────────────────────────┬──────────────┘
                │                               │
┌───────────────▼───────────────┐   ┌───────────▼───────────────┐
│        Service Layer          │   │      Verification Layer    │
│  AuditEventService (write)     │   │  ChainVerificationService  │
│  AuditQueryService (read)      │   │  RedactionService           │
│  RetentionService              │   │  ExportBundleService        │
└───────────────┬───────────────┘   └───────────┬───────────────┘
                │                               │
┌───────────────▼───────────────────────────────▼───────────────┐
│                     Hash Chain Core (domain)                   │
│   HashChainService — computeRecordHash(), canonicalize(),      │
│   linkToPrevious(), genesisHash()                               │
└───────────────┬─────────────────────────────────────────────────┘
                │
┌───────────────▼───────────────────────────────────────────────┐
│                    Persistence (Spring Data JPA)                │
│   AuditRecordRepository   AuditRecordEntity   Flyway migrations │
└───────────────┬───────────────────────────────────────────────┘
                │
                ▼
                PostgreSQL
```

Key design principle: **the hash-chain logic lives in one isolated component (`HashChainService`)** that nothing else reaches around. Every write goes through it; every verify walk goes through it. This is the piece you want rock-solid and heavily unit-tested, since it's the crux of the whole assignment.

---

## 3. Data Model

### 3.1 `audit_record` table

| Column | Type | Notes |
|---|---|---|
| `id` | UUID (PK) | Server-generated, not exposed as sequence (avoid leaking record count / enabling guess attacks) |
| `sequence_no` | BIGINT, unique, monotonic | The chain's ordering key — **do not rely on `created_at` for ordering**, clocks skew; use a DB sequence |
| `event_type` | VARCHAR | e.g. `USER_LOGIN` |
| `actor_id` | VARCHAR | indexed |
| `resource_type` | VARCHAR | indexed |
| `resource_id` | VARCHAR | indexed (composite index with resource_type) |
| `payload` | JSONB | structured event detail |
| `event_timestamp` | TIMESTAMPTZ | caller-supplied (see decision below) |
| `recorded_at` | TIMESTAMPTZ | server-assigned, always — audit truth of *when we saw it* |
| `record_hash` | CHAR(64) | SHA-256 hex of canonical content |
| `previous_hash` | CHAR(64) | hash of prior record, or genesis constant |
| `status` | ENUM(`ACTIVE`,`ARCHIVED`,`REDACTED`) | for Scenario B |
| `redacted_fields` | JSONB, nullable | metadata about what was redacted (see §6.2) |

**Design decision to document explicitly (graders want to see you reasoned about it):**
> Timestamp: accept `timestamp` as caller-supplied but **do not trust it for ordering or hash-chain sequencing**. Store it as `event_timestamp` (informational) and separately stamp `recorded_at` server-side. The hash chain's order of truth is `sequence_no`, assigned atomically at insert time. This avoids a caller backdating events to slot them into the chain wherever they like.

### 3.2 Genesis record

Define a constant, e.g. `previous_hash = SHA256("AUDIT-CHAIN-GENESIS-v1")`, documented in code and in the architecture doc. Record #1's `previous_hash` is this constant — never null, never all-zeros (ambiguous with "not yet computed").

---

## 4. Hash Chain Design (the core of the assignment)

### 4.1 What goes into a record's own hash

Canonicalize a **fixed, ordered** set of fields — not the whole entity (exclude `id`, `status`, `redacted_fields`, since those must be mutable for Scenario B without breaking earlier logic... see redaction design below for how status/archival stays out of the hash safely):

```
canonical_string =
  eventType + "|" + actorId + "|" + resourceType + "|" + resourceId + "|"
  + canonicalJson(payload) + "|" + eventTimestamp.toInstant() + "|"
  + sequenceNo + "|" + previousHash

record_hash = SHA-256(canonical_string)
```

- `canonicalJson(payload)`: sort object keys recursively, fixed number formatting, UTF-8 — write a small deterministic serializer or configure Jackson (`ObjectMapper.configure(SORT_PROPERTIES_ALPHABETICALLY, true)` + `OrderedMap`-style TreeMap re-serialization). **This is a classic AI-assistance trap**: an LLM will often hand you `objectMapper.writeValueAsString(payload)` without canonicalization — flag this in your AI usage log as a caught/corrected suggestion, it's a great authenticity signal for the reviewers.
- Pipe-delimited with delimiters that can't collide is fine for a prototype; document the limitation (a value containing `|` isn't escaped) as a known trade-off, or switch to length-prefixed encoding if you want to close the gap.

### 4.2 Write path

1. Begin transaction.
2. Lock/read the current max `sequence_no` (or use a DB sequence + `SELECT ... FOR UPDATE` on a "chain head" row to avoid race conditions under concurrent writes — **call this out as a concurrency risk you identified**, required by §4 "Validation and Risk Control").
3. Compute `previous_hash` = `record_hash` of the row at that sequence, or genesis.
4. Compute `record_hash` for the new row.
5. Insert.
6. Commit.

### 4.3 `GET /audit/verify`

Walk records ordered by `sequence_no` ascending:
- Recompute each record's `record_hash` from its stored fields (skipping redacted/archived per Scenario B rules — see §6).
- Compare to stored `record_hash` → mismatch = **content tampering**.
- Compare stored `previous_hash` to the prior record's `record_hash` → mismatch = **chain-linkage tampering** (e.g., a row deleted/reordered).
- Report the **first** inconsistency: `sequenceNo`, `recordId`, `violationType` (`CONTENT_MISMATCH` | `LINKAGE_BROKEN` | `MISSING_RECORD` — gap in sequence numbers), and continue-or-stop is your call (recommend: report first break, but also return a summary count of all breaks found after it, since a partial demo is more convincing).

Response shape:
```json
{
  "chainIntact": false,
  "recordsChecked": 4821,
  "firstViolation": {
    "sequenceNo": 1183,
    "recordId": "…",
    "violationType": "CONTENT_MISMATCH",
    "detail": "Stored hash does not match recomputed hash"
  },
  "additionalViolations": 2
}
```

---

## 5. Scenario A — API Surface

| Method | Path | Purpose |
|---|---|---|
| POST | `/audit/events` | Append event (only write op; no PUT/DELETE exposed anywhere) |
| GET | `/audit/events` | Query with `actorId`, `resourceType`, `resourceId`, `eventType`, `from`, `to`, `page`, `size` |
| GET | `/audit/events/{id}` | Fetch single record |
| GET | `/audit/verify` | Chain verification |
| GET | `/audit/verify?fromSeq=&toSeq=` | Optional range verification (nice-to-have, useful for large chains) |

Pagination: keyset pagination on `sequence_no` (not offset) — offset pagination degrades badly and is worth flagging as a deliberate choice.

**Tamper demo for your own validation**: after seeding events, run a raw SQL `UPDATE audit_record SET payload = ... WHERE sequence_no = X`, hit `/audit/verify`, show it now reports `CONTENT_MISMATCH` at X. This is explicitly called out in the assignment as *the* validation step — script it (a `.http` file or a shell script using `curl` + `psql`) so it's repeatable and demoable live.

---

## 6. Scenario B — Retention, Redaction, Export

### 6.1 Retention / Archival

- Config: `audit.retention.window` (e.g. `P90D` via `java.time.Period`/Duration).
- Scheduled job (`@Scheduled` or a manual admin endpoint, since assignment doesn't require a scheduler) flips `status = ARCHIVED` on records older than the window. **Soft-delete only — never a real DELETE**, or you've broken the hash chain's ability to detect gaps.
- `/audit/verify` must treat `ARCHIVED` differently from `MISSING`:
  - If truly soft-deleted (row retained, flagged): verification still recomputes hash normally — nothing changes.
  - If you implement *hard* archival (row moved out of the primary table into an `audit_record_archive` table, or exported+purged): you need an **archival manifest** — a record of "records N..M were archived on date D, archive bundle hash = H" stored in the primary chain itself as a special synthetic event (e.g. `eventType = SYSTEM_ARCHIVAL`) so `/audit/verify` can skip the gap without treating it as tampering. This is the more interesting/defensible design — document both options and which you picked and why.

### 6.2 Structured Redaction (the hardest part — plan this carefully)

**The core problem**: `record_hash` was computed over the original payload. Redact the payload → hash no longer matches → looks like tampering, which is the opposite of what you want.

**Recommended design — redact-in-place with a pre-computed field-level commitment:**

1. At write time, in addition to `record_hash`, also store a **per-field hash map** for any field marked redactable in the payload schema (or just for `payload` as a whole plus a salted per-field hash for a defined set of sensitive keys), e.g.:
   ```
   field_commitments = { "payload.accountNumber": SHA256(salt + value), ... }
   ```
   Store `field_commitments` as part of what feeds `record_hash` (so it's covered by tamper-evidence from day one), and store the `salt` (per-record, random) separately — needed later to *prove* a redacted value's commitment without revealing the value.

2. On redaction:
   - Replace the sensitive value in `payload` with a tombstone marker, e.g. `"[REDACTED:sha256:ab12…]"` — literally embedding the field's original commitment hash as the placeholder.
   - Set `status = REDACTED`, populate `redacted_fields` metadata (which fields, when, by whom, why).
   - **Do not recompute `record_hash`.** `record_hash` stays exactly as originally computed — it's now unverifiable by *directly* recomputing from current `payload`, and that's expected and documented.

3. `/audit/verify` special-cases `REDACTED` records: instead of recomputing `record_hash` from current payload (which would now fail), it verifies that:
   - The current payload's tombstone markers match the stored `field_commitments`.
   - The **non-redacted** fields, combined with the stored commitments (not raw values) in place of the redacted ones, still reproduce `record_hash`.
   
   In other words: redaction is designed in from the start as "hash covers commitments, not raw values, for any field flagged as *potentially* redactable" — this is the trick that makes it work. A field that was never flagged as redactable can't be redacted without breaking the chain (by design — that's your documented limitation/trade-off).

**Alternative (simpler, weaker) approach** worth documenting even if you don't build it: keep the original encrypted at rest, redact only the *displayed* value via an application-layer view, and treat "redaction" as an access-control problem rather than a data-mutation problem. Cheaper, but doesn't satisfy "must be redactable to satisfy data privacy requirements" if you have a real deletion obligation (e.g., GDPR erasure) — encrypted-but-retained data usually doesn't satisfy erasure requirements. **Write this trade-off up explicitly** — this exact discussion is what the rubric means by "risks, trade-offs, and failure scenarios."

### 6.3 Bulk Export

`GET /audit/export?resourceId=X` or `?actorId=X` →
```json
{
  "exportedAt": "...",
  "recordCount": 42,
  "records": [ { …record fields…, "sequenceNo": ..., "recordHash": ..., "previousHash": ... } ],
  "chainContext": {
    "firstSequenceNo": 100,
    "lastSequenceNo": 141,
    "hashOfLastRecordBeforeRange": "…",   // lets recipient anchor into the larger chain
    "exportSignature": "SHA256(sorted record hashes concatenated)"  // bundle-level integrity check
  }
}
```
Recipient can (a) recompute each record's hash from its content, (b) walk the linkage within the bundle, (c) verify `exportSignature` covers exactly these records unaltered. Document that verifying *inclusion in the full chain* (not just internal consistency) would require either a Merkle proof against a periodically published chain root, or trusting the exporting system — call this out as a scoped limitation if you don't implement a Merkle root.

---

## 7. Scenario C — Compliance Reporting (ambiguous requirement)

Don't write code first. Deliverable is a short doc + scoped implementation. Suggested flow:

1. **Clarify.** Write out, in the repo, the raw requirement ("Regulators need to be able to audit access to client account data") and the ambiguities:
   - "Access" — read access only, or read+write+permission-change?
   - "Client account data" — which resource types count? Do we already tag these consistently in `resourceType`?
   - Who is "regulators" as an API consumer — a person via UI, an automated export, a scheduled report to a third party?
   - Time range / retention expectations for compliance data specifically (may differ from Scenario B's general retention policy)?
   - Format requirements (structured export, human-readable report, both)?
2. **Assumptions you'll state and proceed on** (pick reasonable ones, write them down): "access" = any event where `resourceType` is in a configured allow-list of client-account-data types and `eventType` indicates a read/view/export action; consumer = compliance officer via authenticated API + CSV/JSON export; time range = last 7 years default, configurable.
3. **Design**: a thin reporting layer on top of existing query/export APIs — `GET /audit/compliance-report?resourceType=CLIENT_ACCOUNT&from=&to=&format=csv` — reusing `AuditQueryService` and `ExportBundleService`, plus an access-control check (only a `COMPLIANCE_OFFICER` role can call it).
4. **Scope out explicitly**: real regulator delivery/transmission, PII masking rules beyond what Scenario B already built, scheduled/automatic delivery. State these as "not implemented, here's why, here's what it'd take."

This scenario is scored heavily on the *reasoning artifact*, not the code — spend proportionally more time on the write-up than the implementation.

---

## 8. Project Structure

```
audit-log-service/
├── ATTESTATION.md
├── README.md                        # setup instructions
├── ARCHITECTURE.md                  # §2–§6 write-up, decisions & trade-offs
├── AI_USAGE_LOG.md                  # §9 below
├── ENGINEERING_SUMMARY.md           # final summary per §4/§7 of assignment
├── docs/
│   ├── scenario-a.md
│   ├── scenario-b.md
│   └── scenario-c.md
├── pom.xml
├── src/main/java/com/example/auditlog/
│   ├── AuditLogServiceApplication.java
│   ├── config/           (SecurityConfig, JacksonConfig, OpenApiConfig)
│   ├── controller/       (AuditEventController, AuditVerifyController, ExportController, ComplianceReportController)
│   ├── service/          (AuditEventService, AuditQueryService, HashChainService,
│   │                       ChainVerificationService, RetentionService, RedactionService, ExportBundleService)
│   ├── domain/            (AuditRecordEntity, AuditRecordStatus, ViolationType)
│   ├── repository/       (AuditRecordRepository)
│   ├── dto/               (requests/responses, mappers)
│   └── exception/        (GlobalExceptionHandler, validation errors)
├── src/main/resources/
│   ├── application.yml
│   ├── application-test.yml
│   └── db/migration/     (Flyway V1__init.sql, V2__retention_redaction.sql, ...)
├── src/test/java/...     (unit tests per service, Testcontainers integration tests, tamper-detection test)
└── scripts/
    ├── seed-events.sh
    ├── tamper-demo.sh    # raw SQL UPDATE + verify call, scripted end-to-end
    └── export-demo.sh
```

---

## 9. AI Usage Traceability (do this continuously, not at the end)

`AI_USAGE_LOG.md` — one entry per meaningful AI interaction:

```markdown
## [2026-08-05 14:20] HashChainService.canonicalize()
**Prompt intent:** Generate a canonical JSON serializer for hashing.
**AI output:** Suggested `objectMapper.writeValueAsString(payload)` directly.
**Decision:** REJECTED — non-deterministic key ordering across Jackson versions
  would silently break hash reproducibility. Replaced with recursive TreeMap-based
  canonicalization (see commit abc1234).
**Human sign-off:** [you], required because this affects the core tamper-evidence guarantee.
```

Also worth: a short commit-trailer convention, e.g. `AI-Assisted: yes (Claude, prompt: chain verification edge cases)` on relevant commits, so the log and git history cross-reference each other.

---

## 10. Execution Plan (2–3 Days)

**Day 1 — Foundation + Scenario A**
1. Repo init, Spring Boot skeleton, Flyway baseline, `ATTESTATION.md`, CI-less local build green.
2. `AuditRecordEntity` + migration, `HashChainService` with unit tests (canonicalization, genesis, tamper detection at the pure-function level — no DB needed).
3. Write API + validation.
4. Query API + pagination.
5. `ChainVerificationService` + `/audit/verify`.
6. Integration test: seed → tamper via raw SQL → verify → assert violation reported. Script it (`tamper-demo.sh`).
7. Commit throughout (aim for 10+ commits, not one mega-commit).

**Day 2 — Scenario B**
1. Retention/archival design decision write-up first, then migration + service + scheduled/admin endpoint.
2. Redaction design write-up (§6.2) — this is the part to think hardest about before coding.
3. Implement field-commitment scheme, redaction endpoint, verify-service special case.
4. Export endpoint + bundle integrity fields.
5. Tests: retention doesn't false-positive on verify; redaction doesn't break verify; export bundle self-verifies.

**Day 3 — Scenario C + Hardening + Docs**
1. Scenario C clarification doc, scoped design, thin implementation.
2. Security pass: authN/authZ on write + admin endpoints, input validation edges, basic rate-limit note (even if not implemented, document as a risk).
3. `ARCHITECTURE.md`, `ENGINEERING_SUMMARY.md`, `docs/scenario-*.md` written up.
4. Test coverage review, README setup instructions verified on a clean checkout.
5. Final AI usage log pass — make sure every non-trivial AI-assisted change is represented.
6. Push, grant panel access.

---

## 11. Testing Approach to Document

- **Unit**: `HashChainService` (canonicalization determinism, genesis handling, hash stability across field reordering in payload), `RedactionService` (commitment scheme correctness).
- **Integration (Testcontainers + real Postgres)**: full write→query→verify flow; the raw-SQL tamper test; retention/archival not breaking verify; redaction not breaking verify; concurrent-write race condition test (fire N parallel writes, assert chain still links correctly — this proves out the concurrency risk you flagged in §4.2).
- **What's explicitly NOT covered** (state it, don't hide it): load/performance testing beyond a basic large-chain verify timing note; multi-node/distributed chain consistency (out of scope for a prototype); real regulator delivery in Scenario C.

---

## 12. Security & Production-Readiness Notes to Include in ARCHITECTURE.md

- AuthN/AuthZ: API-key or JWT-based; write endpoint requires an authenticated service identity (the `actorId` shouldn't be self-asserted by an anonymous caller for a real audit system — flag this as a hardening item even if your prototype trusts the caller for simplicity).
- No update/delete endpoints exist anywhere in the controller layer — enforced by omission, not just by convention; worth a unit test asserting no such mapping exists.
- SQL injection: JPA/parameterized queries throughout — no string-concatenated queries.
- Hash algorithm: SHA-256 chosen over MD5/SHA-1 (broken) and over SHA-3 (no strong reason to deviate from the well-supported default) — document as a deliberate choice.
- Sensitive payload data: redaction scheme (§6.2) is your privacy control; note that payloads should ideally be encrypted at rest in a true production system, scoped out here for time.

---

## 13. Deliverables Checklist (map back to §7 of the assignment)

- [ ] Private repo + real commit history
- [ ] `ATTESTATION.md`
- [ ] Working prototype + `README.md` setup instructions
- [ ] `ARCHITECTURE.md`
- [ ] `docs/scenario-a.md`, `docs/scenario-b.md`, `docs/scenario-c.md`
- [ ] Testing approach section (in README or a `TESTING.md`)
- [ ] `AI_USAGE_LOG.md`
- [ ] `ENGINEERING_SUMMARY.md`
