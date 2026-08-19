# Security — Audit Log Service

Honest statement of current security posture. Per the project's own principle (`docs/REQUIREMENTS.md` NFR7): where full production security is out of scope, the gap is documented, not silently skipped or implied to be handled.

## Current state (Phase 1, 2026-08-20): no authentication or authorization is enforced

Every endpoint (`POST /audit/events`, `GET /audit/events`, `GET /audit/events/{id}`, `GET /audit/verify`) is reachable by any caller with network access to the service. This is a **known, deliberate gap for this phase**, not an oversight — the engineer directed that Phase 1 scope stay limited to "Project Foundation + Scenario A Core Domain," with security foundation work limited to interfaces, not enforcement.

## What exists

- `com.auditlog.security.AuthenticatedPrincipal` — a plain record (`subjectId`, `tenantId`, `roles`). Forward-declared only: nothing in the codebase constructs one.
- `com.auditlog.security.ResourceAuthorization` — an interface (`isAuthorized(principal, resourceType, resourceId)`) with **no implementation**. A no-op or always-true implementation was deliberately not written, since either would misrepresent authorization as present when it is not.

Neither type is referenced from any controller or service. They exist purely so that later signatures (`AuditQueryService`, a future `RedactionService`/`ExportBundleService`, a future compliance-report endpoint) don't require a larger rewrite when authorization is actually built.

## What is intended (not yet built)

- **Authentication mechanism:** `docs/DECISIONS.md` ADR-008 currently records this as `Proposed`, not `Accepted` — a minimal API-key stub was the original default suggestion, but the engineer's direction for this phase states "JWT/OIDC remains the intended authentication architecture." This is noted as the current intent; ADR-008 has not been formally updated to reflect it yet, and no implementation exists either way.
- **Tenant/resource authorization:** `docs/EVALUATION_CLOSURE_MATRIX.md` item 3 (`SEC-03`) requires this to distinguish authentication, tenant identity, user identity, role, and resource ownership as separate concepts (not a flat "JWT → role → endpoint" model), and to apply to query, redaction, export, and compliance endpoints specifically. Not started.
- **Negative auth tests** (missing/invalid/expired/forged token, incorrect issuer/audience) — `docs/EVALUATION_CLOSURE_MATRIX.md` item 16 (`SEC-02`) — blocked on the authentication mechanism actually existing.

## Other security-relevant notes for Phase 1

- **SQL injection:** all data access goes through Spring Data JPA / parameterized JPQL (`AuditRecordRepository`) — no string-concatenated queries anywhere in the codebase.
- **Append-only enforcement:** no `PUT`/`PATCH`/`DELETE` mapping exists on any audit endpoint, enforced both by omission and by `AppendOnlyApiTest` (fails the build if one is ever added). This is an integrity control, not an access control — it doesn't require authentication to hold.
- **Database permissions:** the H2 database identity the application connects as is not restricted from issuing `UPDATE`/`DELETE` against `audit_record` at the database-permission level. See `docs/DECISIONS.md` ADR-001, point 5, and `docs/EVALUATION_CLOSURE_MATRIX.md` item 9 — explicitly not solved by H2's permission model, tracked as an open item.
- **Secrets:** no secrets exist in this codebase yet (no API keys, credentials, or tokens are used or stored). `docs/EVALUATION_CLOSURE_MATRIX.md` items 7 (secret management) and 8 (TLS) remain open.
- **CORS, request/body limits:** not configured. `docs/EVALUATION_CLOSURE_MATRIX.md` items 5-6 remain open.
- **Logging:** no structured security-event logging exists yet. `docs/EVALUATION_CLOSURE_MATRIX.md` item 18 (`SEC-08`) remains open; no request bodies, headers, or secrets are currently logged, but this hasn't been verified by a dedicated test yet either (there's nothing to log yet, since there are no auth headers or secrets in the system).

## Summary

Phase 1 built the domain and the tamper-evidence mechanism the whole system depends on. It intentionally did not build access control on top of it. Anyone treating this build as production-ready without Phase 2's security work would be wrong to do so — that is exactly why this document exists.
