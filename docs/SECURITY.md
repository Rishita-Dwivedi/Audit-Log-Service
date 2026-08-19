# Security — Audit Log Service

Honest statement of current security posture. Per the project's own principle (`docs/REQUIREMENTS.md` NFR7): where full production security is out of scope, the gap is documented, not silently skipped or implied to be handled.

## Current state (2026-08-20, Milestone 7): authentication and tenant authorization are implemented

Every endpoint under `/audit/**` requires a valid Bearer JWT (`JwtAuthenticationFilter`). Query/fetch endpoints are scoped to the caller's own tenant; `GET /audit/verify` requires the `AUDITOR` role. See `docs/DECISIONS.md` ADR-008 and ADR-012 for the full design and alternatives considered.

## ⚠️ `POST /dev/auth/token` is NOT a real authentication endpoint

This cannot be stated too plainly: this endpoint issues a valid, signed JWT for **any** requested `subjectId`/`tenantId`/`roles` with **zero identity verification** — no password, no external check of any kind. It exists solely because this environment has no real OIDC identity provider to validate against, and the system needs to be demoable and testable end-to-end regardless. **A real production deployment must delete this endpoint** (or hard-gate it behind a build profile that can never ship) and replace token issuance with a real IdP (JWKS-based verification against Okta/Auth0/Keycloak/etc. — see `docs/DECISIONS.md` ADR-008 for what that would involve).

## What is implemented and tested

- **Authentication:** HMAC-signed JWT (HS256), validated on every request except `/dev/auth/token` itself. Missing, malformed, expired, forged (wrong signing key), wrong-issuer, and wrong-audience tokens are all rejected with 401 — tested in `JwtServiceTest` (unit) and `SecurityAuthenticationTest` (integration), covering `docs/EVALUATION_CLOSURE_MATRIX.md` item 16 (`SEC-02`).
- **Tenant isolation:** `tenant_id` is part of every record's hashed content; derived only from the JWT, never from request bodies; query/fetch are always scoped to the caller's own tenant unless they hold `ROLE_AUDITOR`; cross-tenant `findById` returns 404 (not 403) to avoid confirming another tenant's data exists. Tested in `TenantIsolationTest` (7 tests), covering item 3 (`SEC-03`) and item 21 (`TEST-04`).
- **Role-gated system-wide operations:** `GET /audit/verify` requires `ROLE_AUDITOR` — a regular tenant cannot see system-wide chain state. See `docs/DECISIONS.md` ADR-012 for why verify is deliberately not tenant-scoped.
- **SQL injection:** unchanged from Phase 1 — all data access goes through Spring Data JPA / parameterized JPQL.
- **Append-only enforcement:** unchanged from Phase 1 — `AppendOnlyApiTest` still passes; authentication does not weaken this guarantee (it's structural, not access-control-based).

## What is still NOT implemented

- **Resource-level (non-tenant) authorization.** `com.auditlog.security.ResourceAuthorization` remains an unimplemented interface — tenant isolation was implemented as a direct check, not through this interface (`docs/DECISIONS.md` ADR-012). If a future requirement needs finer-grained per-resource ACLs beyond tenant boundaries, this is the extension point, but nothing consults it today.
- **`docs/EVALUATION_CLOSURE_MATRIX.md` items 5-10 (request/body limits, CORS, secret management, TLS, immutable DB permissions, operational monitoring)** — none of these are Milestone 7's scope; tracked separately for a later "Security + negative testing" pass.
- **Redaction, export, compliance-report endpoint authorization** — these endpoints don't exist yet (Milestones 8-10). `SEC-03`'s requirement that they be tenant-authorized will be addressed when they're built, using the same pattern established here.
- **Real secret management for the JWT signing key** — it's a plaintext value in `application.yml` (dev-only, clearly labeled), not sourced from a secret manager. Tracked as item 7 (`SEC-09`).

## Database permissions (unchanged from Phase 1)

The H2 database identity the application connects as is still not restricted from issuing `UPDATE`/`DELETE` against `audit_record` at the database-permission level (`docs/DECISIONS.md` ADR-001, point 5; `docs/EVALUATION_CLOSURE_MATRIX.md` item 9). Authentication/authorization added in this milestone control *who can call the API*, not *what the application's own database credentials are permitted to do directly against the database* — these are different layers, and closing one does not close the other.
