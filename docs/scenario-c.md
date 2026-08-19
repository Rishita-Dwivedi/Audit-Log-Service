# Scenario C — Compliance Reporting: Clarification and Design

Per the assignment: this requirement is **intentionally under-specified**, and the deliverable is demonstrating the clarification process itself, not just an implementation. This document is written *before* any Scenario C code, per the assignment's explicit instruction ("do not silently interpret this").

## Raw requirement, as given

> "Regulators need to be able to audit access to client account data."

That is the entire requirement. Everything else in this document is this engineer's clarification of it.

## Ambiguities identified

1. **What does "access" mean?** Read-only (viewing/querying client data)? Or does it also cover writes (creating/updating records, granting permissions)? The word "access" leans toward "read," but a compliance/regulatory context often cares just as much about who *changed* something as who merely *viewed* it.
2. **What counts as "client account data"?** The system already records events against a free-text `resourceType` field (`docs/REQUIREMENTS.md` FR-A1). Nothing in Scenario A or B establishes a canonical set of resource types that constitute "client account data" versus, say, internal system events.
3. **Who is "regulators," as an API consumer?** A human regulator with direct API credentials? An internal compliance officer preparing a report *for* regulators? An automated scheduled export to a third party? These have very different authentication, authorization, and delivery implications.
4. **What time range applies?** Regulatory retention/reporting windows are often much longer (years) than an operational retention policy (`docs/DECISIONS.md` ADR-004's `window-days`, default 90). Should there be a default range at all, or should the caller always specify one?
5. **What format is expected?** Structured (JSON) for programmatic consumption? Human-readable (CSV/PDF)? Both?
6. **Does a compliance report bypass redaction?** If a field was legitimately redacted (`docs/DECISIONS.md` ADR-003), should a regulator/compliance officer see the original value, or the same tombstoned view everyone else sees?

## Assumptions made (and why), rather than silently picking one

Each assumption below is a real design decision this engineer made to make the requirement concrete. Where a reasonable alternative exists, it's named.

1. **"Access" means any recorded interaction, not read-only.** The audit log already doesn't distinguish "this event was a read" from "this event was a write" as a structural concept — `eventType` is a free-text field (`USER_LOGIN`, `RECORD_UPDATED`, `PERMISSION_GRANTED`, etc.), and Scenario A never introduced a read/write taxonomy. Building one now, just for this endpoint, would be scope creep for a requirement that doesn't clearly demand it. A regulator auditing "access to client account data" plausibly wants the full picture — who viewed it *and* who changed it — so the compliance report surfaces every event against allow-listed resource types, with `eventType` visible so a reviewer can filter further by the nature of the interaction. *Alternative not chosen:* restrict to a curated list of "read-like" event types (e.g., only `*_VIEWED`, `*_QUERIED`) — rejected because the system has no enforced `eventType` naming convention, so this would be unreliable and could silently exclude genuinely relevant events.
2. **"Client account data" is a configurable resource-type allow-list, defaulting to `ACCOUNT`.** Rather than hardcoding a fixed set (which the actual production taxonomy would almost certainly not match), the allow-list is a config value (`audit.compliance.client-data-resource-types`), so it can be extended without a code change once a real product/compliance stakeholder defines the actual list. *Alternative not chosen:* asking the engineer's own judgment to enumerate an exhaustive list of "client account data" resource types — rejected because this system doesn't have enough business context to know what that list should really be; that's exactly the kind of question the assignment says to flag rather than silently answer.
3. **"Regulators" means an internal compliance officer consuming the API directly, not an external regulator or a scheduled third-party delivery.** A new role, `ROLE_COMPLIANCE_OFFICER` (already reserved in `com.auditlog.security.Roles` since Milestone 7), gates this endpoint. Real regulator delivery (giving an external party direct credentials, or a scheduled export pipeline to a third-party system) is explicitly **out of scope** — see below.
4. **`from` and `to` are both required, with no default range.** Unlike the general query API (`GET /audit/events`, where omitting filters returns everything), a compliance report silently defaulting to some time window risks producing an *incomplete* report that looks complete to whoever requested it — a materially worse failure mode for a regulatory context than an explicit 400 error forcing the caller to state their intended range. *Alternative considered:* default to a long window (e.g., 7 years, as informally suggested during early planning) — rejected as the default behavior because a compliance officer should have to think about and state their range explicitly, not inherit an assumption they may not know exists.
5. **Format: JSON only.** Matches the rest of this API; CSV/PDF human-readable export is explicitly scoped out (see below) — JSON is trivially convertible downstream by whatever tool a compliance team already uses for reporting.
6. **No redaction bypass.** The compliance report shows data exactly as stored — if a field was redacted, the report shows the same tombstone every other caller sees. The system has no mechanism to reverse a redaction (that's the point of the field-commitment scheme, `docs/DECISIONS.md` ADR-003 — it's a one-way operation), so building a "compliance officers can see through redaction" capability would be substantial new work with real privacy implications of its own, not a small addition. This is scoped out explicitly, not silently assumed away.

## Clarified requirement statement

> An authenticated caller holding the `COMPLIANCE_OFFICER` role can retrieve a paginated list of all audit events recorded against a configured allow-list of "client account data" resource types, within an explicitly bounded time range they must specify, scoped to a tenant they specify (or across all tenants if none is specified — this role is cross-tenant capable, like `AUDITOR`). The report reflects the current state of each record, including any legitimate redaction already applied. Real-time regulator access, scheduled third-party delivery, and human-readable export formats are explicitly out of scope for this implementation.

## Design

Implemented as a thin layer over the existing query infrastructure (per the original architecture plan), not a new subsystem:

- `GET /audit/compliance-report?tenantId=&from=&to=&afterSequenceNo=&pageSize=`
- `ComplianceReportService` — gates on `ROLE_COMPLIANCE_OFFICER`, requires `from`/`to`, queries `AuditRecordRepository.findForComplianceReport()` (a dedicated query filtering by the allow-listed resource types, reusing the same keyset-pagination pattern as `AuditQueryService`).
- Response reuses `AuditEventPageResponse` (the same shape `GET /audit/events` returns) — genuinely thin, no new response model.

## Scoped out (stated explicitly, not silently dropped)

- **Real regulator delivery/transmission** (direct external-party API access, scheduled export pipelines to a third party). Would need its own authentication story (external identity, not this system's internal JWT), delivery/retry semantics, and almost certainly a completely different trust model than an internal compliance officer querying the API directly.
- **PII masking beyond what Scenario B already built.** The report shows whatever redaction already exists; it does not apply any additional masking of its own.
- **Human-readable export formats** (CSV, PDF). JSON only.
- **A formal, enforced read/write taxonomy for `eventType`.** "Access" is treated as "any recorded interaction" specifically because building this taxonomy is a bigger undertaking than this requirement, as stated, justifies.

## Status

See `docs/REQUIREMENTS.md` FR-C1 (this document) and FR-C2 (the implementation below) for acceptance criteria and test evidence.
