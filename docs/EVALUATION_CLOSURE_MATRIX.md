# Evaluation Closure Matrix — Secure Audit Log Service

This matrix tracks closure of every failure and partial gap identified by the previous engineering evaluation (gate-adjusted verdict: **WEAK BORDERLINE**), treated as explicit quality requirements for this implementation. It is a living document: it is created/revised before any application code exists in this repository, so every item below starts `Open`. It is updated in place as design decisions are made, code is written, tests are added, and evidence is produced — it is not rewritten retroactively to look complete.

**Revision note:** this version incorporates a more detailed restatement of the previous evaluation's findings than the version this matrix started from — the same 24 underlying findings, but several IDs came with additional required-improvement detail (e.g., `SEC-09` now names two further sub-requirements beyond secret management and TLS; `TEST-09` now names class coverage and thresholds explicitly; `SEC-03` now names tenant isolation and BOLA/IDOR explicitly). Where new detail changed a row's content, it is folded in; where an ID's required improvements span genuinely distinct sub-requirements, they remain separate rows, consistent with how this matrix was already structured. **Item numbering has changed from the previous revision of this file** because two new `SEC-09` sub-items were inserted — see the Summary table for the current numbering; do not rely on item numbers cited in earlier discussion.

No application code has been written as part of producing this document. No items are marked implemented, tested, or closed.

**Terminology:** IDs and requirement wording below are carried over exactly as given from the previous evaluation. No additional failures beyond the 26 rows below (covering the same 24 underlying findings) have been added.

**Cross-references:** where an item touches an existing decision in `docs/DECISIONS.md` or a design element already in `docs/ARCHITECTURE.md` / `docs/REQUIREMENTS.md`, that is called out explicitly, including where an existing decision is now too narrow and needs revisiting — that is a finding of *this* document, not an invented new failure. Documentation-evidence pointers below reference the proposed documentation set from the current planning round (`docs/SECURITY.md`, `docs/TESTING.md`, `docs/QUALITY_GATES.md`, `docs/ENDPOINT_TEST_MATRIX.md`, `docs/PERFORMANCE.md`, `docs/IMPLEMENTATION_PLAN.md`) — these files do not exist yet; see the accompanying proposal for their structure.

## Status legend

| Status | Meaning |
|---|---|
| Open — Not Started | No design decision, code, or test exists yet. |
| Open — Design Pending | A prerequisite design decision (new or revised ADR) must be made before implementation can start. |
| Open — Blocked by X | Cannot be meaningfully implemented/tested until X (another item, or a named prerequisite decision) is resolved. |

`In Progress`, `Implemented — Pending Test`, and `Verified — Closed` are reserved for future updates to this same document as work proceeds.

## Reviewer sign-off legend

`Pending` — not yet reviewed/approved. `Approved by <name>, <date>` — to be filled in only once actual evidence (code, test, documentation, runtime artifact, commit) exists and has been reviewed. No item in this version carries a sign-off.

---

## Summary

| # | Previous evaluation ID | Item | Priority | Status |
|---|---|---|---|---|
| 1 | ATT-01 | Delivery commit identifiability | P0 | Open — Not Started |
| 2 | TEST-09 | JaCoCo coverage generation, thresholds & mapping | P0 | Open — Not Started |
| 3 | SEC-03 | Tenant/resource ownership authorization (tenant isolation, BOLA/IDOR prevention) | P0 | Open — Design Pending |
| 4 | SEC-06 | Replay/idempotency protection | P1 | Open — Not Started |
| 5 | SEC-06 | Global request/body limits | P1 | Open — Not Started |
| 6 | SEC-06 | Explicit CORS policy | P1 | Open — Not Started |
| 7 | SEC-09 | Secret management & rotation strategy | P1 | Open — Not Started |
| 8 | SEC-09 | TLS/HTTPS deployment evidence | P1 | Open — Not Started |
| 9 | SEC-09 | Immutable DB permissions for audit records | P1 | Open — Design Pending |
| 10 | SEC-09 | Operational monitoring documentation | P1 | Open — Not Started |
| 11 | TEST-06 | Database fault/rollback tests | P1 | Open — Not Started |
| 12 | TEST-06 | Multi-instance/concurrent contention testing | P1 | Open — Design Pending |
| 13 | TEST-08 | Reproducible CI/test artifacts & Testcontainers reporting | P1 | Open — Design Pending |
| 14 | ARC-02 | External chain-head anchor | P2 | Open — Design Pending (feasibility to be assessed) |
| 15 | ARC-03 | Signed export manifests | P2 | Open — Not Started |
| 16 | SEC-02 | Invalid/expired/forged/missing authentication tests | Gap | Open — Blocked by ADR-008 (auth mechanism) |
| 17 | SEC-05 | Oversized/malformed request body tests | Gap | Open — Blocked by #5 |
| 18 | SEC-08 | PII/log-injection/security-event tests | Gap | Open — Design Pending |
| 19 | TEST-01 | Endpoint-to-requirement test matrix | Gap | Open — Not Started |
| 20 | TEST-03 | Expanded malformed/boundary testing | Gap | Open — Not Started |
| 21 | TEST-04 | Tenant/BOLA/cross-resource tests | Gap | Open — Blocked by #3 |
| 22 | TEST-05 | Replay/duplicate semantics tests | Gap | Open — Blocked by #4 |
| 23 | TEST-06 | Fault injection, rollback, idempotency tests (combined) | Gap | Open — Blocked by #4, #11 |
| 24 | TEST-08 | Reproducible execution evidence preservation | Gap | Open — Blocked by #13 |
| 25 | ARC-02 | External anchor trade-off documentation | Gap | Open — Blocked by #14 |
| 26 | ARC-03 | Export signature trust model documentation | Gap | Open — Blocked by #15 |

---

## P0

### 1. ATT-01 — Delivery commit identifiability

| Field | Value |
|---|---|
| Previous evaluation ID | ATT-01 |
| Problem identified | The previous evaluation found that the exact delivery commit was not reliably identifiable, and there was no guarantee the submitted archive corresponded exactly to that commit. |
| Required remediation | Record the exact delivery commit SHA; distinguish baseline revision from delivery revision; ensure the submitted archive corresponds exactly to the delivery revision; use a release/tag process where appropriate. |
| Design decision | Not yet decided. New ADR required in `docs/DECISIONS.md` covering: what counts as the "baseline revision" (e.g., the commit where Scenario A work begins) versus the "delivery revision" (the final submitted commit) as two distinct, separately recorded references; a tagging convention for the delivery commit; a documented, repeatable procedure for verifying that any submitted archive matches that commit's tree exactly (e.g., `git archive` from the tag, with a recorded checksum). |
| Implementation task | None — this is a process/traceability control, not application code. Add a submission-procedure section to `README.md` once decided. |
| Unit test | N/A — process/traceability control, not a code-level test. |
| Integration test | N/A |
| Security test | N/A |
| Documentation evidence | To be added: submission procedure in `README.md`; baseline/delivery revision convention and delivery commit SHA recorded in `docs/DECISIONS.md` and in `ATTESTATION.md` (attestation must point to the exact delivery revision, per submission-time discipline). |
| Runtime/reproduction evidence | To be produced at submission time: the delivery tag/commit hash and a checksum of the corresponding archive, recorded together. |
| Git commit | None — no commits exist in the repository yet. |
| Status | Open — Not Started |
| Reviewer sign-off | Pending |

### 2. TEST-09 — JaCoCo coverage generation, thresholds & mapping

| Field | Value |
|---|---|
| Previous evaluation ID | TEST-09 |
| Problem identified | The previous evaluation found no quantitative JaCoCo coverage evidence. |
| Required remediation | Generate JaCoCo line, branch, method, and class coverage; produce HTML/XML reports; integrate into Maven verification; define appropriate thresholds; preserve CI artifacts; map coverage to endpoint/security risk. |
| Design decision | Not yet decided. New ADR required covering: JaCoCo Maven plugin binding (unit + integration test phases), the specific coverage thresholds to enforce (and whether the build fails below them), where reports are preserved as build artifacts, and how coverage is mapped to endpoint/security risk — proposed approach is to extend `docs/ENDPOINT_TEST_MATRIX.md` (see proposal) with a coverage column rather than maintaining two disconnected artifacts, with threshold values themselves recorded in `docs/QUALITY_GATES.md`. |
| Implementation task | None yet — Maven `pom.xml` plugin configuration is application/build tooling, not written at this stage. |
| Unit test | N/A — tooling/reporting requirement, not a discrete test case; it measures the tests defined elsewhere in this matrix. |
| Integration test | N/A |
| Security test | N/A |
| Documentation evidence | To be added: coverage thresholds and rationale in `docs/QUALITY_GATES.md`; coverage-to-endpoint/risk mapping in `docs/ENDPOINT_TEST_MATRIX.md`. |
| Runtime/reproduction evidence | To be produced: JaCoCo HTML/XML report generated by the build, preserved as a build artifact; class-level coverage summary included. |
| Git commit | None — no commits exist in the repository yet. |
| Status | Open — Not Started |
| Reviewer sign-off | Pending |

### 3. SEC-03 — Tenant/resource ownership authorization

| Field | Value |
|---|---|
| Previous evaluation ID | SEC-03 |
| Problem identified | The previous evaluation found that tenant/resource authorization was unresolved: tenant isolation and resource ownership/authorization were not implemented, leaving the system exposed to BOLA/IDOR, and this was untested for query, redaction, export, and compliance endpoints. |
| Required remediation | Implement tenant isolation; implement resource ownership/authorization; prevent BOLA/IDOR; query endpoints must be tenant-scoped; redaction must be tenant/resource authorized; export must be tenant authorized; compliance reporting must be tenant authorized; add explicit cross-tenant negative tests (tracked as item 21, `TEST-04`, the dedicated test counterpart, rather than duplicated here). |
| Design decision | Not yet decided, and this **supersedes the current scope of `docs/DECISIONS.md` ADR-008** (`Proposed`, minimal API-key check only, no ownership model). Per the security-model principle now made explicit (authorization is not simply "JWT → role → endpoint"), the revised/replacement ADR must separately define: authentication (who is calling — depends on ADR-008's mechanism being finalized first), tenant identity, user identity, role, resource ownership, and tenant/resource authorization as distinct concepts, plus the concrete check applied per request across the four named endpoint categories (query, redaction, export, compliance). This is the single highest-leverage open design decision in this matrix — several other items (16, 21) are blocked on it. |
| Implementation task | None yet. Will require an authorization component consulted by `AuditQueryService`, `RedactionService`, `ExportBundleService`, and the compliance-report endpoint (`ComplianceReportController`, per `docs/ARCHITECTURE.md`). |
| Unit test | Planned: authorization-decision unit tests (allow/deny cases) for the ownership-check component in isolation, covering tenant identity, resource ownership, and role independently. |
| Integration test | Planned: per-endpoint integration tests (query, redaction, export, compliance) asserting a denied response when the caller does not own/is not entitled to the requested resource, and that no cross-tenant data is leaked in the response (not just that access is denied — see `docs/REQUIREMENTS.md`-level acceptance criteria to be added). |
| Security test | Planned: dedicated cross-tenant/BOLA test suite — see item 21 (`TEST-04`), the testing counterpart to this requirement. |
| Documentation evidence | To be added: revised/replacement ADR in `docs/DECISIONS.md`; authorization model described in `docs/SECURITY.md`; updated `docs/ARCHITECTURE.md` cross-cutting concerns section. |
| Runtime/reproduction evidence | To be produced: reproducible request examples (e.g., `.http` file or script) showing a cross-tenant request denied on each of the four endpoint categories. |
| Git commit | None — no commits exist in the repository yet. |
| Status | Open — Design Pending |
| Reviewer sign-off | Pending |

---

## P1

### 4. SEC-06 — Replay/idempotency protection

| Field | Value |
|---|---|
| Previous evaluation ID | SEC-06 |
| Problem identified | The previous evaluation found that replay/idempotency protection was not implemented, allowing duplicate/replayed writes. |
| Required remediation | Define an idempotency strategy; prevent duplicate/replayed writes. |
| Design decision | Not yet decided. New ADR required covering the mechanism (e.g., an idempotency-key header on `POST /audit/events`, deduplication window/storage) and how it interacts with the append-only write path in `docs/ARCHITECTURE.md`. |
| Implementation task | None yet. |
| Unit test | Planned: idempotency-key handling logic (duplicate key within window vs. new key) tested in isolation. |
| Integration test | Planned: submitting the same write twice with the same idempotency key results in a single persisted record. |
| Security test | Planned: replay of a captured request is rejected/deduplicated rather than creating a duplicate audit event — see also item 22 (`TEST-05`). |
| Documentation evidence | To be added: new ADR in `docs/DECISIONS.md`; behavior documented in `docs/ARCHITECTURE.md` API design section and `docs/SECURITY.md`. |
| Runtime/reproduction evidence | To be produced: reproduction script sending a duplicate request and showing a single resulting record. |
| Git commit | None — no commits exist in the repository yet. |
| Status | Open — Not Started |
| Reviewer sign-off | Pending |

### 5. SEC-06 — Global request/body limits

| Field | Value |
|---|---|
| Previous evaluation ID | SEC-06 |
| Problem identified | The previous evaluation found that global request/body limits were not implemented. |
| Required remediation | Define maximum request/payload sizes; reject oversized requests appropriately. |
| Design decision | Not yet decided. New ADR required covering the maximum request/body size and where it is enforced (e.g., embedded server configuration vs. a filter), applied globally rather than per-endpoint. |
| Implementation task | None yet. |
| Unit test | N/A — this is primarily a container/framework configuration concern; covered by integration/security tests instead. |
| Integration test | Planned: a request under the limit succeeds; behavior at the boundary is tested (see item 20, `TEST-03`). |
| Security test | Planned: an oversized request body is rejected — see item 17 (`SEC-05`), the testing counterpart to this requirement. |
| Documentation evidence | To be added: new ADR in `docs/DECISIONS.md`; limit value and rationale documented in `docs/SECURITY.md`. |
| Runtime/reproduction evidence | To be produced: reproduction request exceeding the configured limit, showing rejection. |
| Git commit | None — no commits exist in the repository yet. |
| Status | Open — Not Started |
| Reviewer sign-off | Pending |

### 6. SEC-06 — Explicit CORS policy

| Field | Value |
|---|---|
| Previous evaluation ID | SEC-06 |
| Problem identified | The previous evaluation found that no explicit CORS policy was defined. |
| Required remediation | Define explicit CORS policy. |
| Design decision | Not yet decided. New ADR required covering allowed origins/methods/headers for this service, consistent with the fact that the assignment states no external application/consumer is required — the default posture is likely restrictive (no cross-origin access) unless a concrete consumer is identified, particularly relevant to Scenario C (`docs/REQUIREMENTS.md`, FR-C1/FR-C2). |
| Implementation task | None yet. |
| Unit test | N/A — configuration concern. |
| Integration test | Planned: a request from a disallowed origin is rejected/not granted CORS headers; an allowed configuration (if any) is verified. |
| Security test | Planned: verify no wildcard/overly permissive CORS configuration is present. |
| Documentation evidence | To be added: new ADR in `docs/DECISIONS.md`; policy and rationale in `docs/SECURITY.md`. |
| Runtime/reproduction evidence | To be produced: reproduction request demonstrating the configured CORS behavior. |
| Git commit | None — no commits exist in the repository yet. |
| Status | Open — Not Started |
| Reviewer sign-off | Pending |

### 7. SEC-09 — Secret management & rotation strategy

| Field | Value |
|---|---|
| Previous evaluation ID | SEC-09 |
| Problem identified | The previous evaluation found that secret management and rotation strategy was not documented or implemented. |
| Required remediation | Define production secret management; define secret rotation strategy; do not commit secrets. |
| Design decision | Not yet decided. New ADR required covering how secrets (e.g., the credential from ADR-008, once implemented) are stored, injected into the application (env vars/config, not hard-coded), and a rotation approach — proportionate to a prototype, likely documentation-led with implementation limited to "no secret in source control" rather than a full rotation service. |
| Implementation task | None yet. |
| Unit test | N/A |
| Integration test | N/A |
| Security test | Planned: a check (manual or automated, e.g., a secret-scanning step) that no secret value is committed to the repository. |
| Documentation evidence | To be added: new ADR in `docs/DECISIONS.md`; secret-handling instructions in `docs/SECURITY.md` and `README.md`. |
| Runtime/reproduction evidence | To be produced: demonstration that the application reads its secret from environment/config rather than source. |
| Git commit | None — no commits exist in the repository yet. |
| Status | Open — Not Started |
| Reviewer sign-off | Pending |

### 8. SEC-09 — TLS/HTTPS deployment evidence

| Field | Value |
|---|---|
| Previous evaluation ID | SEC-09 |
| Problem identified | The previous evaluation found that no TLS deployment evidence was provided. |
| Required remediation | Document TLS termination/deployment; provide reproducible local TLS evidence where real production deployment is unavailable. |
| Design decision | Not yet decided. New ADR required — likely approach for a local prototype is a reproducible local HTTPS setup (e.g., a self-signed certificate + documented local TLS-enabled run profile) rather than a real deployment, since no production deployment target exists for this assignment. |
| Implementation task | None yet. |
| Unit test | N/A |
| Integration test | N/A |
| Security test | Planned: verify plain-HTTP access is disabled or documented as a non-default profile once TLS is configured. |
| Documentation evidence | To be added: new ADR in `docs/DECISIONS.md`; local TLS setup instructions in `docs/SECURITY.md` and `README.md`. |
| Runtime/reproduction evidence | To be produced: reproducible steps/script to run the service over HTTPS locally. |
| Git commit | None — no commits exist in the repository yet. |
| Status | Open — Not Started |
| Reviewer sign-off | Pending |

### 9. SEC-09 — Immutable DB permissions for audit records

| Field | Value |
|---|---|
| Previous evaluation ID | SEC-09 |
| Problem identified | The previous evaluation found that database permissions did not prevent the application's own database identity from modifying or deleting audit records — i.e., the database layer itself offered no independent enforcement of append-only behavior beyond the application code's choice not to expose update/delete endpoints (`docs/REQUIREMENTS.md` FR-A1). |
| Required remediation | Define database permissions so audit records cannot be modified/deleted by the application DB identity, where practical. |
| Design decision | Not yet decided, and there is a **direct tension with `docs/DECISIONS.md` ADR-001** (H2 only). H2's support for fine-grained, revocable per-statement privileges (e.g., `GRANT`/`REVOKE` restricting `UPDATE`/`DELETE` on `audit_record` for the application's own connection role) is more limited than a server-grade RDBMS like PostgreSQL. This item cannot be satisfied by application-layer omission of update/delete endpoints alone — that is already covered by FR-A1/ADR-010 and is a distinct, weaker guarantee than a DB-enforced permission. Options to evaluate: (a) configure the strictest H2 user-privilege restriction available and document its actual limits honestly rather than overstating them, or (b) revisit ADR-001 for this specific concern while keeping H2 for other purposes, or (c) document this as a "where practical" limitation not fully achievable on H2, with the mitigation being ADR-010's application-layer enforcement plus the hash chain itself as the actual detection mechanism (tampering that bypasses the app is still caught by `/audit/verify`, which is arguably the more central guarantee for this system). Not decided here — flagged as a genuine open question. |
| Implementation task | None yet. |
| Unit test | N/A |
| Integration test | N/A |
| Security test | Planned: attempt an `UPDATE`/`DELETE` against `audit_record` using the application's configured DB credentials directly (bypassing the app) and confirm it is rejected at the DB layer, once a permission model is chosen — if H2's limits make this only partially achievable, the test documents exactly what is and is not enforced. |
| Documentation evidence | To be added: new ADR in `docs/DECISIONS.md` resolving the tension above; documented in `docs/SECURITY.md` and `docs/ARCHITECTURE.md`. |
| Runtime/reproduction evidence | To be produced: reproduction script attempting a direct DB mutation and showing the actual (not aspirational) result. |
| Git commit | None — no commits exist in the repository yet. |
| Status | Open — Design Pending |
| Reviewer sign-off | Pending |

### 10. SEC-09 — Operational monitoring documentation

| Field | Value |
|---|---|
| Previous evaluation ID | SEC-09 |
| Problem identified | The previous evaluation found that operational monitoring was not documented. |
| Required remediation | Document operational monitoring. |
| Design decision | Not yet decided. For a prototype of this scope, likely a documentation-only item: what should be monitored in a real deployment (e.g., `/audit/verify` failure rate/alerts, write-path error rate, chain-verification latency, authentication/authorization failure rate as a security signal) and what, if anything, is actually instrumented in the prototype versus described as a production gap. |
| Implementation task | None yet — likely documentation-only, with at most basic Spring Boot Actuator health/metrics endpoints as a minimal concrete instrumentation, if included. |
| Unit test | N/A |
| Integration test | N/A |
| Security test | N/A |
| Documentation evidence | To be added: monitoring section in `docs/SECURITY.md` or `docs/ARCHITECTURE.md`, stating what is implemented vs. documented-only. |
| Runtime/reproduction evidence | To be produced only if actuator/metrics endpoints are implemented: sample output. |
| Git commit | None — no commits exist in the repository yet. |
| Status | Open — Not Started |
| Reviewer sign-off | Pending |

### 11. TEST-06 — Database fault/rollback tests

| Field | Value |
|---|---|
| Previous evaluation ID | TEST-06 |
| Problem identified | The previous evaluation found no fault injection and no rollback verification. |
| Required remediation | Add transaction rollback tests; add DB failure/fault tests. |
| Design decision | Not yet decided in isolation, but constrained by `docs/DECISIONS.md` ADR-001 (H2 only). Fault injection needs to be reasoned about within an H2-based test setup (e.g., simulated constraint violations, forced transaction failure) rather than assuming a Testcontainers/Postgres-specific fault-injection mechanism. |
| Implementation task | None yet. |
| Unit test | N/A — this is inherently an integration-level concern (real transaction boundaries). |
| Integration test | Planned: a write that fails mid-transaction (e.g., a forced constraint violation) leaves no partial record and does not advance `sequence_no`, verified against the H2 test database. |
| Security test | N/A |
| Documentation evidence | To be added: fault-injection approach documented in `docs/TESTING.md`. |
| Runtime/reproduction evidence | To be produced: test run output showing the rollback scenario passes. |
| Git commit | None — no commits exist in the repository yet. |
| Status | Open — Not Started |
| Reviewer sign-off | Pending |

### 12. TEST-06 — Multi-instance/concurrent contention testing

| Field | Value |
|---|---|
| Previous evaluation ID | TEST-06 |
| Problem identified | The previous evaluation found incomplete multi-instance contention testing. |
| Required remediation | Add concurrency tests; add a multi-instance contention strategy/test, where practical. |
| Design decision | Directly tied to the concurrency risk already flagged, but not resolved, in `docs/ARCHITECTURE.md` ("Concurrency" under Hash-chain approach): concurrent writers must not claim the same `sequence_no` or link to a stale previous hash. The underlying locking/atomic-assignment strategy itself still needs to be decided (new ADR) before it can be tested. **The "where practical" qualifier matters here**: true multi-instance testing (multiple separate application processes/nodes contending against one database) is materially more infrastructure than a single-process prototype naturally has; the realistic scope is concurrent-thread contention within one running instance, with true multi-instance behavior addressed by documented reasoning (the same DB-level locking strategy should generalize to multiple app instances) rather than a literal multi-process test, unless time permits building one. This scoping choice needs to be made explicit in the ADR, not silently assumed. |
| Implementation task | None yet. |
| Unit test | N/A — concurrency behavior is not meaningfully unit-testable in isolation. |
| Integration test | Planned: fire N parallel writes (within one instance) and assert the resulting chain has no duplicate `sequence_no`, no gaps, and correct linkage. |
| Security test | N/A |
| Documentation evidence | To be added: concurrency-control ADR in `docs/DECISIONS.md`, referenced from `docs/ARCHITECTURE.md`; scope of "multi-instance" testing stated explicitly in `docs/TESTING.md`. |
| Runtime/reproduction evidence | To be produced: test run output/log showing N concurrent writers resulting in an intact chain. |
| Git commit | None — no commits exist in the repository yet. |
| Status | Open — Design Pending |
| Reviewer sign-off | Pending |

### 13. TEST-08 — Reproducible CI/test artifacts & Testcontainers reporting

| Field | Value |
|---|---|
| Previous evaluation ID | TEST-08 |
| Problem identified | The previous evaluation found reproducibility not evidenced, and Docker/Testcontainers results not supplied. |
| Required remediation | Provide a reproducible test command; CI execution; Surefire reports; JaCoCo reports; explicit Docker/Testcontainers skip/failure reporting; preserve artifacts in CI. |
| Design decision | Not yet decided, and there is a direct conflict to resolve with `docs/DECISIONS.md` ADR-001, which selected H2-only (no Testcontainers) specifically to avoid a Docker dependency. This item cannot be satisfied by silently ignoring Testcontainers — it requires an explicit decision: either (a) a documented statement that Testcontainers is intentionally not used, with the reproducibility story built entirely around H2 (and the "explicit skip reporting" requirement satisfied by stating this plainly rather than having a silently-skipped test), or (b) revisiting ADR-001. Recorded here as unresolved, not decided unilaterally by this document. |
| Implementation task | None yet. |
| Unit test | N/A |
| Integration test | N/A |
| Security test | N/A |
| Documentation evidence | To be added: resolution of the ADR-001 conflict above; documented test-execution/report-artifact convention in `docs/TESTING.md` and `docs/QUALITY_GATES.md` (e.g., Surefire/Failsafe + JaCoCo reports preserved as build artifacts). |
| Runtime/reproduction evidence | To be produced: a documented, repeatable command (e.g., `mvn verify`) whose output/artifacts constitute the reproducible evidence, plus an explicit statement of what is or is not skipped and why. |
| Git commit | None — no commits exist in the repository yet. |
| Status | Open — Design Pending |
| Reviewer sign-off | Pending |

---

## P2

### 14. ARC-02 — External chain-head anchor

| Field | Value |
|---|---|
| Previous evaluation ID | ARC-02 |
| Problem identified | The previous evaluation found a newest-tail deletion limitation remaining: deleting the most recent record(s) directly from the data store is not detectable by `/audit/verify`, since there is nothing after the deleted tail to reveal a broken linkage. |
| Required remediation | Investigate external chain-head anchoring; document design; implement if feasible; otherwise document limitation and mitigation clearly. |
| Design decision | Not yet decided. New ADR required covering where the external anchor is stored (outside the primary `audit_record` table/database instance — e.g., a separate append-only log, file, or external service holding the latest known `record_hash`/`sequence_no`), how often it is updated, and how `/audit/verify` uses it to detect a truncated tail. **Per the softened remediation wording here** (implement if feasible, otherwise document), feasibility must be assessed explicitly and the outcome recorded either way — this is not an unconditional implementation mandate like most other items in this matrix. |
| Implementation task | None yet — contingent on the feasibility assessment above. |
| Unit test | N/A until design exists. |
| Integration test | Planned, if implemented: delete the newest record(s) directly from the data store; verify against the external anchor detects the truncation that in-table verification alone would miss. |
| Security test | N/A |
| Documentation evidence | To be added: new ADR in `docs/DECISIONS.md` (including the feasibility assessment itself, even if the outcome is "not implemented"); updated Verification approach section in `docs/ARCHITECTURE.md`. See also item 25 (trade-off documentation for this same item). |
| Runtime/reproduction evidence | To be produced, if implemented: reproduction script demonstrating tail-deletion detection via the anchor. If not implemented: a clear statement of the residual limitation and its mitigation. |
| Git commit | None — no commits exist in the repository yet. |
| Status | Open — Design Pending (feasibility to be assessed) |
| Reviewer sign-off | Pending |

### 15. ARC-03 — Signed export manifests

| Field | Value |
|---|---|
| Previous evaluation ID | ARC-03 |
| Problem identified | The previous evaluation found an unsigned export trust boundary: the bundle-level integrity value described in `docs/ARCHITECTURE.md` (a hash over included record hashes) is unsigned, so a recipient cannot verify who produced the bundle, only that its stated contents are internally consistent. |
| Required remediation | Sign export manifests; implement signature verification; document trust model and key management assumptions. |
| Design decision | Not yet decided. New ADR required covering the signing mechanism (e.g., asymmetric signature over the export manifest/integrity value), key management for the signing key (intersects with items 7 and 9, secret management and DB-identity permissions), and how a recipient verifies the signature independently. |
| Implementation task | None yet. |
| Unit test | Planned: signature generation/verification logic tested in isolation once a scheme is chosen. |
| Integration test | Planned: an exported bundle's signature verifies successfully; a tampered bundle fails signature verification. |
| Security test | Planned: verify a bundle with a stripped or forged signature is rejected by the verification logic. |
| Documentation evidence | To be added: new ADR in `docs/DECISIONS.md`; updated Bulk export section in `docs/ARCHITECTURE.md`. See also item 26 (trust-model documentation for this same item). |
| Runtime/reproduction evidence | To be produced: reproduction script exporting a bundle, verifying its signature, then showing a tampered copy fails verification. |
| Git commit | None — no commits exist in the repository yet. |
| Status | Open — Not Started |
| Reviewer sign-off | Pending |

---

## Additional partial gaps

### 16. SEC-02 — Invalid/expired/forged/missing authentication tests

| Field | Value |
|---|---|
| Previous evaluation ID | SEC-02 |
| Problem identified | The previous evaluation found this only partially addressed: missing token, invalid token, expired token, forged token, and (where applicable) incorrect issuer/audience were not tested. |
| Required remediation | Test missing token; invalid token; expired token; forged token; incorrect issuer/audience where applicable. |
| Design decision | No new design decision of its own — depends entirely on the authentication mechanism from `docs/DECISIONS.md` ADR-008 being finalized first. **This is a genuinely distinct blocker from item 3 (`SEC-03`)**: ADR-008 governs *authentication* (who is calling), while item 3 governs *tenant/resource authorization* (what they're allowed to touch) — the two must not be conflated, per the security-model principle stated for this phase. The "issuer/audience" sub-requirement only applies if a JWT-based mechanism is chosen; it is not applicable to a simple API-key scheme. This is itself a reason the ADR-008 choice needs to be revisited now rather than left as the earlier "minimal API-key" default, given `SEC-02`'s expectations. |
| Implementation task | None yet — this item is test coverage, contingent on authentication existing. |
| Unit test | N/A |
| Integration test | Planned: requests with a missing credential, a malformed/forged credential, and (if a time-bound/JWT credential is chosen) an expired credential and an incorrect issuer/audience are each tested against a protected endpoint and rejected. |
| Security test | Planned: this is itself the security test category requested; covered together with the integration tests above rather than as a separate suite. |
| Documentation evidence | To be added: authentication behavior documented in `docs/SECURITY.md` once ADR-008 is finalized. |
| Runtime/reproduction evidence | To be produced: reproduction requests for each auth-failure mode, showing rejection. |
| Git commit | None — no commits exist in the repository yet. |
| Status | Open — Blocked by ADR-008 (auth mechanism) |
| Reviewer sign-off | Pending |

### 17. SEC-05 — Oversized/malformed request body tests

| Field | Value |
|---|---|
| Previous evaluation ID | SEC-05 |
| Problem identified | The previous evaluation found this only partially addressed: malformed payload, oversized payload, and validation boundaries were not tested. |
| Required remediation | Test malformed payload; test oversized payload; test validation boundaries. |
| Design decision | No new design decision of its own for malformed-body handling (standard Bean Validation, per `docs/REQUIREMENTS.md` NFR6/FR-A1 acceptance criteria); the oversized-body case depends on item 5 (`SEC-06` global request/body limits) being decided and implemented first. |
| Implementation task | None yet. |
| Unit test | N/A |
| Integration test | Planned: malformed JSON, wrong field types, and missing required fields against `POST /audit/events` are each tested for a 4xx response with no record persisted. |
| Security test | Planned: an oversized request body is tested against the limit from item 5 once implemented. |
| Documentation evidence | To be added: covered as part of `docs/ENDPOINT_TEST_MATRIX.md` (item 19). |
| Runtime/reproduction evidence | To be produced: test run output covering each malformed-body case. |
| Git commit | None — no commits exist in the repository yet. |
| Status | Open — Blocked by #5 (for the oversized-body case; malformed-body tests can proceed independently once the write API exists) |
| Reviewer sign-off | Pending |

### 18. SEC-08 — PII/log-injection/security-event tests

| Field | Value |
|---|---|
| Previous evaluation ID | SEC-08 |
| Problem identified | The previous evaluation found this only partially addressed: PII logging risks, log injection, and security-event logging were not tested, and there was no assurance that request bodies, auth headers, or secrets were excluded from logs. |
| Required remediation | Add dedicated PII/log-injection/security-event tests; ensure request bodies, auth headers, and secrets are not logged. |
| Design decision | Not yet decided. New ADR required covering: what counts as a "security event" worth dedicated logging (e.g., authentication failures, authorization denials from item 3, redaction operations from `docs/DECISIONS.md` ADR-003), and an explicit logging convention that (a) never logs raw request bodies, (b) never logs authentication headers/tokens/secrets in any form, and (c) sanitizes/rejects values that could perform log injection (embedded newlines/control characters). |
| Implementation task | None yet. |
| Unit test | Planned: a log-sanitization utility (if introduced) tested for not emitting raw PII/sensitive field values, auth headers, or secrets. |
| Integration test | Planned: a request containing a value that would attempt log injection does not corrupt or split log entries; a request with an auth header does not have that header appear in application logs. |
| Security test | Planned: security-relevant events (auth failure, authorization denial, redaction) are confirmed to appear in logs without leaking the underlying sensitive value, request body, or credential. |
| Documentation evidence | To be added: new ADR in `docs/DECISIONS.md`; logging conventions in `docs/SECURITY.md`. |
| Runtime/reproduction evidence | To be produced: sample log output demonstrating a security event logged without PII/credential leakage. |
| Git commit | None — no commits exist in the repository yet. |
| Status | Open — Design Pending |
| Reviewer sign-off | Pending |

### 19. TEST-01 — Endpoint-to-requirement test matrix

| Field | Value |
|---|---|
| Previous evaluation ID | TEST-01 |
| Problem identified | The previous evaluation found this only partially addressed: a formal endpoint-to-requirement test matrix did not exist. |
| Required remediation | Create a formal endpoint-to-requirement test matrix. |
| Design decision | No new architectural decision required — this is a documentation/traceability artifact. Proposed approach: `docs/ENDPOINT_TEST_MATRIX.md`, keyed by the endpoints in `docs/ARCHITECTURE.md`'s API design section, mapped to the `FR-A#`/`FR-B#`/`FR-C#` requirement IDs in `docs/REQUIREMENTS.md` and to the specific test(s) that exercise each, with JaCoCo coverage (item 2, `TEST-09`) attached per row once available. |
| Implementation task | None — documentation artifact, not application code. |
| Unit test | N/A |
| Integration test | N/A |
| Security test | N/A |
| Documentation evidence | To be added: `docs/ENDPOINT_TEST_MATRIX.md`, populated as tests are written (see proposal). |
| Runtime/reproduction evidence | N/A until populated with real test results. |
| Git commit | None — no commits exist in the repository yet. |
| Status | Open — Not Started |
| Reviewer sign-off | Pending |

### 20. TEST-03 — Expanded malformed/boundary testing

| Field | Value |
|---|---|
| Previous evaluation ID | TEST-03 |
| Problem identified | The previous evaluation found this only partially addressed: malformed/boundary testing needed to be expanded. |
| Required remediation | Expand malformed/boundary testing. |
| Design decision | No new architectural decision — this extends the validation behavior already specified in `docs/REQUIREMENTS.md` FR-A1/FR-A2 acceptance criteria to explicit boundary cases (e.g., empty strings vs. missing fields, maximum field lengths, pagination boundary values, time-range edge cases on `from`/`to`). |
| Implementation task | None yet. |
| Unit test | Planned: boundary-value unit tests for validation rules once defined. |
| Integration test | Planned: boundary-value integration tests per endpoint, tracked in `docs/ENDPOINT_TEST_MATRIX.md` (item 19). |
| Security test | N/A |
| Documentation evidence | To be added: boundary cases enumerated in `docs/ENDPOINT_TEST_MATRIX.md`. |
| Runtime/reproduction evidence | To be produced: test run output covering boundary cases. |
| Git commit | None — no commits exist in the repository yet. |
| Status | Open — Not Started |
| Reviewer sign-off | Pending |

### 21. TEST-04 — Tenant/BOLA/cross-resource tests

| Field | Value |
|---|---|
| Previous evaluation ID | TEST-04 |
| Problem identified | The previous evaluation found this only partially addressed: explicit tenant/BOLA/cross-resource tests were missing. |
| Required remediation | Add explicit tenant/BOLA/cross-resource tests. |
| Design decision | This is the direct testing counterpart to item 3 (`SEC-03`); no separate design decision — depends on the ownership/authorization model from item 3 being defined first. |
| Implementation task | None yet. |
| Unit test | N/A — covered under item 3's authorization-decision unit tests. |
| Integration test | Planned: cross-tenant/cross-resource access attempts against query, redaction, export, and compliance endpoints (mirroring item 3's four endpoint categories) each assert denial and confirm no cross-tenant data leaks in the response body. |
| Security test | Planned: this item is itself the dedicated BOLA/cross-tenant security test suite referenced from item 3. |
| Documentation evidence | To be added: tracked in `docs/ENDPOINT_TEST_MATRIX.md` once authorization exists. |
| Runtime/reproduction evidence | To be produced: reproduction requests demonstrating denial for each endpoint category. |
| Git commit | None — no commits exist in the repository yet. |
| Status | Open — Blocked by #3 |
| Reviewer sign-off | Pending |

### 22. TEST-05 — Replay/duplicate semantics tests

| Field | Value |
|---|---|
| Previous evaluation ID | TEST-05 |
| Problem identified | The previous evaluation found this only partially addressed: replay/duplicate semantics were not tested. |
| Required remediation | Add replay/duplicate semantics tests. |
| Design decision | This is the direct testing counterpart to item 4 (`SEC-06` replay/idempotency protection); no separate design decision — depends on that mechanism being defined first. |
| Implementation task | None yet. |
| Unit test | N/A — covered under item 4's idempotency-key unit tests. |
| Integration test | Planned: sending the same request twice (same idempotency key) results in one record; sending genuinely different requests does not get incorrectly deduplicated. |
| Security test | N/A — functional/idempotency correctness rather than a security boundary in itself, though it supports item 4's security framing (replay protection). |
| Documentation evidence | To be added: tracked in `docs/ENDPOINT_TEST_MATRIX.md` once implemented. |
| Runtime/reproduction evidence | To be produced: reproduction script demonstrating deduplication. |
| Git commit | None — no commits exist in the repository yet. |
| Status | Open — Blocked by #4 |
| Reviewer sign-off | Pending |

### 23. TEST-06 — Fault injection, rollback, and idempotency tests (combined)

| Field | Value |
|---|---|
| Previous evaluation ID | TEST-06 |
| Problem identified | The previous evaluation found this only partially addressed: fault injection, rollback, concurrency, and idempotency tests were missing, beyond what is captured individually in items 11 and 12. |
| Required remediation | Add fault injection, rollback, concurrency, and idempotency tests, exercised together. |
| Design decision | No separate design decision — this item combines the database fault/rollback design from item 11 with the idempotency design from item 4; tracked together here as the previous evaluation's own combined phrasing, without inventing a distinct mechanism. |
| Implementation task | None yet. |
| Unit test | N/A |
| Integration test | Planned: combined scenario tests exercising fault injection alongside idempotency-key replay, to confirm the two mechanisms interact correctly (e.g., a retried request after a transient DB fault is treated as a replay, not a new write). |
| Security test | N/A |
| Documentation evidence | To be added: tracked in `docs/ENDPOINT_TEST_MATRIX.md` once items 4 and 11 are implemented. |
| Runtime/reproduction evidence | To be produced: combined reproduction scenario output. |
| Git commit | None — no commits exist in the repository yet. |
| Status | Open — Blocked by #4, #11 |
| Reviewer sign-off | Pending |

### 24. TEST-08 — Reproducible execution evidence preservation

| Field | Value |
|---|---|
| Previous evaluation ID | TEST-08 |
| Problem identified | The previous evaluation found this only partially addressed: reproducible CI artifacts were not preserved. |
| Required remediation | Preserve reproducible CI artifacts on an ongoing basis. |
| Design decision | No separate design decision — this is the ongoing-practice counterpart to item 13's CI/artifact decision (including the ADR-001/Testcontainers conflict noted there); depends on item 13 being resolved first. |
| Implementation task | None yet. |
| Unit test | N/A |
| Integration test | N/A |
| Security test | N/A |
| Documentation evidence | To be added: a documented convention (once item 13 is resolved) for where execution evidence (test reports, coverage reports, logs) is stored and how it stays current, in `docs/QUALITY_GATES.md`. |
| Runtime/reproduction evidence | To be produced: an example of preserved evidence from an actual build run. |
| Git commit | None — no commits exist in the repository yet. |
| Status | Open — Blocked by #13 |
| Reviewer sign-off | Pending |

### 25. ARC-02 — External anchor trade-off documentation

| Field | Value |
|---|---|
| Previous evaluation ID | ARC-02 |
| Problem identified | The previous evaluation found this only partially addressed: trade-offs of the external anchor approach were not documented. |
| Required remediation | Document external anchor trade-offs. |
| Design decision | No separate design decision — this is the documentation counterpart to item 14; depends on item 14's ADR/feasibility assessment existing first, at which point its trade-offs (e.g., added operational dependency on the external anchor, what happens if the anchor itself is unavailable or tampered with) are written up as part of that same ADR rather than a second, disconnected document. |
| Implementation task | None — documentation task. |
| Unit test | N/A |
| Integration test | N/A |
| Security test | N/A |
| Documentation evidence | To be added: trade-offs section within item 14's ADR in `docs/DECISIONS.md`. |
| Runtime/reproduction evidence | N/A |
| Git commit | None — no commits exist in the repository yet. |
| Status | Open — Blocked by #14 |
| Reviewer sign-off | Pending |

### 26. ARC-03 — Export signature trust model documentation

| Field | Value |
|---|---|
| Previous evaluation ID | ARC-03 |
| Problem identified | The previous evaluation found this only partially addressed: the export signature trust model was not documented. |
| Required remediation | Document export signature trust model. |
| Design decision | No separate design decision — this is the documentation counterpart to item 15; depends on item 15's ADR existing first, at which point the trust model (who holds the signing key, how a recipient obtains/trusts the corresponding verification key, what a valid signature does and does not prove) is written up as part of that same ADR. |
| Implementation task | None — documentation task. |
| Unit test | N/A |
| Integration test | N/A |
| Security test | N/A |
| Documentation evidence | To be added: trust-model section within item 15's ADR in `docs/DECISIONS.md`. |
| Runtime/reproduction evidence | N/A |
| Git commit | None — no commits exist in the repository yet. |
| Status | Open — Blocked by #15 |
| Reviewer sign-off | Pending |
