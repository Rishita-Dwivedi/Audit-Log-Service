# Requirements — Audit Log Service

Normalized requirements derived from the assignment brief ("Interview Assignment: Build an AI-Assisted Software Engineering System — Audit Log Service", v2.0, 2026-08-03). Terminology (`eventType`, `actorId`, `resourceType`, `resourceId`, `payload`, `timestamp`, hash chain, `/audit/verify`, etc.) is kept consistent with the assignment text.

## Problem statement

Build a **tamper-evident audit log service**: a system that records an append-only history of events and guarantees that past records cannot be modified or deleted without detection. The service is validated entirely through its own APIs — write events, query them, verify the chain, modify a record directly in the data store, verify again to confirm detection. No external application or consumer is required.

## Requirement IDs

Functional requirements are numbered `FR-A#`, `FR-B#`, `FR-C#` by scenario. Non-functional requirements are numbered `NFR#`. These IDs are referenced from `ARCHITECTURE.md` and `DECISIONS.md`.

---

## Scenario A — Greenfield: Core Audit Log Service

**Status (2026-08-20, Phase 1): all of FR-A1 through FR-A4 are IMPLEMENTED and TESTED.** 24/24 tests passing (`docs/TESTING.md`), including a live manual demonstration of the mandated write→verify→tamper→verify sequence against a running instance, in addition to the automated integration tests.

### FR-A1 — Write API — IMPLEMENTED, TESTED
Accept an event record containing at minimum: `eventType`, `actorId`, `resourceType`, `resourceId`, `payload` (structured, event-specific), and `timestamp` (caller-supplied or server-assigned — choice documented in `DECISIONS.md`).

**Acceptance criteria**
- A valid request with all required fields is persisted and returns the stored record (including its computed hash fields). — TESTED (`AuditEventCreationTest.createsEventSuccessfully`)
- A request missing a required field is rejected with a 4xx response and no record is persisted. — TESTED (`AuditEventCreationTest.rejectsRequestMissingRequiredFields`)
- Records are **append-only**: no endpoint anywhere in the API allows updating or deleting a stored record. — TESTED (`AppendOnlyApiTest`, reflection-based; fails the build if a mutation mapping is ever added)

### FR-A2 — Query API — IMPLEMENTED, TESTED
Retrieve events filtered by any combination of: `actorId`; `resourceType` and `resourceId`; `eventType`; time range (`from`/`to`). Results are paginated.

**Acceptance criteria**
- Each filter, and combinations of filters, narrow results correctly against a seeded dataset. — TESTED (`AuditEventQueryTest`, one test per filter)
- Omitting all filters returns all records, paginated. — IMPLEMENTED, not independently tested as its own case (implied by the other query tests using an unfiltered baseline).
- Pagination behaves correctly across page boundaries for a result set larger than one page. — TESTED (`AuditEventQueryTest.paginatesUsingCursor`)

### FR-A3 — Tamper Evidence (Hash Chain) — IMPLEMENTED, TESTED
Each stored record includes a hash of its own content (the event fields) and a hash of the immediately preceding record (or a defined genesis value for the first record), forming a hash chain.

**Acceptance criteria**
- Every persisted record has a non-null `record_hash` and `previous_hash`. — TESTED (`AuditEventCreationTest`, `ChainIntegrityTest`)
- The first record in the chain has `previous_hash` equal to a defined, documented genesis value. — TESTED (`ChainIntegrityTest.firstRecordUsesGenesisValue`)
- Recomputing a record's hash from its stored content, independently of the write path, reproduces the stored `record_hash`. — TESTED (`ChainIntegrityTest.verificationSucceedsForAnUntamperedChain`, plus `HashChainServiceTest` at the pure-function level)

### FR-A4 — Chain Verification Endpoint — IMPLEMENTED, TESTED
Expose `GET /audit/verify`, which walks the full chain and reports whether the chain is intact, and — if broken — which record is the first inconsistency and what type of violation was detected.

**Acceptance criteria**
- Against an untampered chain, the endpoint reports the chain as intact. — TESTED
- After a record's content is modified directly in the data store, the endpoint reports the chain as broken, identifies the affected record, and reports a content-mismatch violation type. — TESTED (`TamperDetectionTest.detectsDirectContentModification`) and demonstrated live (see `docs/TESTING.md`)
- After a record is removed directly from the data store, the endpoint detects the resulting break (linkage or sequence gap) and reports it. — TESTED (`TamperDetectionTest.detectsSequenceGapFromDeletedMiddleRecord`, `.deletedMiddleRecordProducesBothMissingRecordAndLinkageViolations`)
- This end-to-end sequence — write, query, verify, direct data-store tamper, verify again — is the assignment's explicit validation path for Scenario A and must be demonstrable and repeatable. — DEMONSTRATED: both as an automated, repeatable test suite and as a one-off live run (app started via the packaged jar, tampered via the H2 shell, `curl`'d against `/audit/verify`).

---

## Scenario B — Extend Scenario A: Retention and Redaction

### FR-B1 — Retention Policy
Records older than a configurable window are archivable or soft-deletable.

**Acceptance criteria**
- The retention window is configurable, not hard-coded.
- Records older than the window can be transitioned to an archived state.
- `GET /audit/verify` handles archived records correctly and does **not** report a false-positive break for records that were legitimately archived per policy.

### FR-B2 — Structured Redaction — IMPLEMENTED, TESTED (Milestone 8, 2026-08-20)
Certain fields within a record's `payload` (e.g., account numbers, personal identifiers) must be redactable to satisfy data privacy requirements, without breaking the hash chain.

**Acceptance criteria**
- A redaction operation on a flagged field replaces the sensitive value such that the original value is no longer present in the stored payload. — TESTED (`RedactionTest.redactingAFieldReplacesItWithATombstoneAndSetsStatus`)
- `GET /audit/verify` continues to treat the redacted record as valid (does not report it as tampered) after redaction. — TESTED (`RedactionTest.verificationStillSucceedsAfterALegitimateRedaction`)
- `GET /audit/verify` still detects genuine tampering of a redacted record (e.g., a further, unauthorized change to the redacted record). — TESTED (`RedactionTest.verifyDetectsAForgedTombstoneOnAnAlreadyRedactedField`, `.verifyDetectsRawPayloadTamperedWithoutUpdatingCommitment`)
- The chosen approach, its trade-offs, and its limitations are documented (see `ARCHITECTURE.md` and `DECISIONS.md`) — the assignment explicitly calls this out as "a genuine engineering problem," not a checkbox. — DOCUMENTED (`docs/DECISIONS.md` ADR-003, expanded with the implementation's verification-gap reasoning and validated test list)

Scope note: only top-level `payload` fields are redactable in this implementation; nested object/array field redaction is out of scope (documented limitation, `ADR-003`). Redaction is tenant-authorized the same way query/fetch are (`ADR-012`) — this also closes `docs/EVALUATION_CLOSURE_MATRIX.md` items 3/21 for the redaction endpoint specifically.

### FR-B3 — Bulk Export
Provide an endpoint to export all records for a given `resourceId` or `actorId` as a self-contained, verifiable bundle, including enough chain metadata for a recipient to independently verify that the records it contains have not been altered since export.

**Acceptance criteria**
- The export bundle contains, for each record, the fields needed to recompute and check its hash and its linkage to the previous record in the bundle.
- The bundle includes an integrity mechanism (documented in `ARCHITECTURE.md`) that lets a recipient detect if the bundle itself was altered after export.
- A recipient with no access to the live database can perform this verification using only the bundle's contents.

---

## Scenario C — Ambiguous: Compliance Reporting

### Raw requirement (as given)
> "Regulators need to be able to audit access to client account data."

This requirement is **intentionally under-specified** by the assignment. Per the assignment's own instructions, this document does not resolve it into invented specifics — that resolution is Scenario C's deliverable, produced through an explicit clarification process before any Scenario C code is written (see `docs/scenario-c.md`, to be authored at the start of Phase 9).

### FR-C1 — Requirement Clarification
Demonstrate, in the repository, how the raw requirement above is clarified and normalized before writing any code.

**Acceptance criteria**
- The clarified requirement statement is written down.
- Identified ambiguities are listed explicitly, for example (not yet resolved, listed here only as known open questions):
  - What does "access" mean — read-only, or read/write/permission-change?
  - What counts as "client account data" — which `resourceType` values?
  - Who consumes the report — a person via API, an automated export, a scheduled delivery to a third party?
  - What time range and format are expected?
- Assumptions made to proceed (or the questions that would be asked of product before proceeding) are stated explicitly, not silently baked into the implementation.

### FR-C2 — Scoped Design and Implementation
Translate the clarified requirement into a concrete technical design, and implement it (or a well-reasoned partial implementation with a documented scope boundary).

**Acceptance criteria**
- The design document states what is implemented versus what is explicitly scoped out, and why.
- The implementation (full or partial) is consistent with the clarified requirement statement and the stated assumptions.

---

## Non-functional requirements

- **NFR1 — Development history:** the repository reflects real, incremental development (commit as you go), not a single mega-commit reconstructed at the end.
- **NFR2 — AI usage traceability:** AI-assisted changes are logged (generated / edited / rejected, with rationale) and connected to the work in the repository (`AI_USAGE_LOG.md`).
- **NFR3 — Quality gates:** the codebase is subject to tests, and consideration of linting/static analysis, security, and performance, even where not all are fully implemented — gaps are documented rather than silently skipped.
- **NFR4 — Human sign-off on high-impact changes:** decisions with significant correctness or security impact (e.g., the redaction scheme, concurrency handling for sequence assignment) are explicitly reviewed and approved by the engineer, not auto-accepted from an AI suggestion.
- **NFR5 — Engineer ownership:** the engineer retains explicit ownership of correctness, maintainability, and production readiness of all output, AI-assisted or not.
- **NFR6 — Production-quality output:** code, API/schema definitions, and tests are written with clean design and maintainability in mind, proportionate to a 2–3 day prototype.
- **NFR7 — Security posture:** even where full production security is out of scope, risks are identified and documented (e.g., authentication/authorization gaps, SQL injection avoidance via parameterized queries).
- **NFR8 — Documentation set:** the repository includes `ATTESTATION.md`, a working prototype with setup instructions, an architecture overview, per-scenario documentation, a testing-approach write-up, `AI_USAGE_LOG.md`, and a final engineering summary.
- **NFR9 — Confidentiality:** the assignment materials are Charles Schwab confidential and proprietary; the raw assignment PDF is not committed to the repository (see `DECISIONS.md`, ADR-007).

## Explicitly out of scope (for this prototype, unless revisited)

- Distributed/multi-node chain consistency.
- Load/performance testing beyond a basic large-chain verification timing note.
- Real regulator delivery/transmission mechanisms for Scenario C.
- A user-facing UI — the assignment states the system is validated through its APIs alone.
- Full production-grade authentication/authorization (role-based access, token issuance, etc.) — a minimal stub is in scope; the full system is documented as a gap.
