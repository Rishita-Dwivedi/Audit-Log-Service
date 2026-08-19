# AI Usage Log

This log records meaningful AI-assisted interactions on this project: what was asked, what the AI produced, and — critically — which parts became final engineering decisions versus which remain AI-suggested drafts awaiting human review. AI used: Claude (Claude Code CLI), acting as an engineering assistant under explicit instruction not to implement application code until directed.

Convention going forward: one entry per meaningful interaction, in chronological order, using the format below. Commits that stem from an AI-assisted interaction should reference the corresponding log entry date in the commit message.

```markdown
## [YYYY-MM-DD] <short title>
**Prompt intent:** what was asked of the AI
**AI output:** what the AI produced/suggested
**Human decision:** accepted / modified / rejected, and why
**Sign-off:** who approved it and why it did or didn't need explicit sign-off
```

---

## [2026-08-20] Repository inspection and requirement analysis

**Prompt intent:** Inspect the (empty) repository, read the assignment PDF carefully, and — without writing any application code — summarize the requirements, separate Scenario A/B/C, split functional vs. non-functional requirements, identify ambiguities and decisions needing human input, propose a phased development plan with task dependencies, and propose an initial Spring Boot architecture and data model.

**AI output:** A full written analysis covering all of the above, including a proposed layered architecture (controller/service/verification/domain-core/persistence), a proposed `audit_record` schema, and an explicit list of ambiguities/decisions requiring human sign-off (persistence choice, redaction scheme, retention mechanism, timestamp/ordering semantics, auth scope, PDF confidentiality handling, Scenario C scope, export integrity model).

**Human decision:** Reviewed as a starting scaffold, not accepted wholesale. The engineer explicitly withheld approval to implement anything and required a documented decision round before proceeding (see next entry).

**Sign-off:** Required and given only for "proceed to documentation" — not for "proceed to implementation," which remains explicitly withheld pending further review.

---

## [2026-08-20] Architecture-critical decisions — human selection among AI-presented options

**Prompt intent:** Implicit in the prior analysis: the AI flagged four decisions as materially affecting the architecture and data model, and presented each as a set of concrete options with an AI recommendation, rather than deciding unilaterally.

**AI output / options presented:**
- Persistence: PostgreSQL+Testcontainers (AI-recommended) vs. H2 only.
- Redaction scheme: field-commitment scheme (AI-recommended) vs. encrypt-at-rest + access control.
- Retention mechanism: soft-delete status flag (AI-recommended for the time-box) vs. hard archival + synthetic manifest event.
- Timestamp/ordering: `sequence_no` as order of truth (AI-recommended) vs. trusting caller-supplied timestamp directly.

**Human decision:** The engineer selected explicitly, and did **not** default to every AI recommendation uniformly — the choices made were: **H2 only** (diverging from the AI's initial recommendation of Postgres), **field-commitment scheme** (matching the AI's recommendation), **soft-delete status flag** (matching the AI's recommendation), **`sequence_no` as order of truth** (matching the AI's recommendation). These four choices are recorded as `Accepted` decisions in `docs/DECISIONS.md` (ADR-001 through ADR-004), attributed to the engineer, not to the AI's default suggestion.

**Sign-off:** Explicit, by direct selection — this is the clearest form of human sign-off in this project so far, since it was a forced choice among stated alternatives rather than passive acceptance of a single AI-authored answer.

---

## [2026-08-20] AI-proposed defaults not yet confirmed

**Prompt intent:** N/A — proactively flagged by the AI during the analysis above, rather than requested.

**AI output:** The AI proposed three lower-stakes defaults without forcing an explicit choice round: (1) exclude the raw assignment PDF from git given its "do not retain after submission" marking, (2) implement a minimal API-key check on write/admin endpoints with full auth documented as a gap, (3) Maven + Java 21.

**Human decision:** Not yet explicitly confirmed or rejected at the time of this entry. Recorded in `docs/DECISIONS.md` as `Proposed` (ADR-007, ADR-008) rather than `Accepted` specifically so this distinction is visible and these are not mistaken for engineer-approved decisions.

**Sign-off:** Pending. These should not be treated as final until the engineer explicitly confirms or overrides them.

---

## [2026-08-20] Initial engineering documentation drafted (REQUIREMENTS.md, ARCHITECTURE.md, DECISIONS.md, ATTESTATION.md, AI_USAGE_LOG.md)

**Prompt intent:** Given the analysis and decisions above, draft the initial engineering documentation set — `docs/REQUIREMENTS.md`, `docs/ARCHITECTURE.md`, `docs/DECISIONS.md`, `ATTESTATION.md`, `AI_USAGE_LOG.md` — using assignment terminology, without inventing requirements not justified by the assignment text, and without writing any application code.

**AI output:** All five files, fully AI-drafted: normalized requirements with acceptance criteria split by scenario; a design-level architecture document (components, API surface, data model, hash-chain/verification/redaction approach); ADR-style decision records with alternatives and trade-offs for each of the ten decisions made or proposed so far; an attestation template with placeholders left open for the engineer's legal name and dates (not fabricated by the AI); and this log itself.

**Human decision:** Pending review at the time of writing — these documents have not yet been read back and approved line-by-line by the engineer, and nothing has been committed to git. They represent an AI-authored first draft produced under explicit scope constraints, not a finished, human-reviewed artifact.

**Sign-off:** Not yet given. Per the project's own stated convention (`NFR4`, `NFR5` in `docs/REQUIREMENTS.md`), this entry will be updated — or a follow-up entry added — once the engineer has reviewed these documents and either approved them as-is or directed changes.

---

## [2026-08-20] Evaluation Closure Matrix drafted, then revised with richer detail

**Prompt intent:** Create `docs/EVALUATION_CLOSURE_MATRIX.md` mapping every item from a previous engineering evaluation (24 findings, P0/P1/P2 plus additional partial gaps) to remediation, design decision, implementation task, test evidence (unit/integration/security), documentation evidence, runtime/reproduction evidence, git commit, status, and reviewer sign-off — without inventing additional failures and using the evaluation's exact terminology. A later prompt supplied a more detailed restatement of the same evaluation (adding, among other things, two further `SEC-09` sub-requirements and sharper wording on several existing items) and asked for the matrix to be updated accordingly.

**AI output:** A first version (24 rows) followed by a full rewrite (26 rows, renumbered) incorporating the additional detail, plus explicit cross-references flagging where the new detail conflicted with an already-made decision — e.g., the new `SEC-09` "immutable DB permissions" and existing `TEST-08` reproducibility requirements were flagged against `ADR-001` (H2-only) as an unresolved tension, rather than silently resolved one way.

**Human decision:** The engineer had not yet responded to the flagged H2/Postgres tension at the time this entry covers — that decision is recorded separately below, once actually made.

**Sign-off:** Reviewed only to the extent the engineer's next message engaged directly with the flagged tension (see the H2 decision entry below); the matrix content itself has not been separately signed off line-by-line.

---

## [2026-08-20] Database decision: H2 selected over PostgreSQL/Testcontainers

**Prompt intent:** Resolve the H2-vs-Postgres tension flagged in the Evaluation Closure Matrix work above.

**AI output:** N/A — this was not an AI recommendation being accepted; the engineer stated the decision directly and instructed the AI to update the architecture/planning documents accordingly and not spend implementation time on Docker.

**Engineer's decision, recorded as given:**
- PostgreSQL/Testcontainers was considered.
- Docker was reported by the engineer as unavailable/impractical in the current environment. (Note: this was not independently re-verified by attempting a Docker/Testcontainers setup — it is recorded as the engineer's stated constraint, not a tested fact.)
- H2 was selected to keep the prototype runnable within the assignment's time-box, via a simple `mvn` command with no external service dependency.
- PostgreSQL/Testcontainers remains an explicitly documented production-compatibility and reproducibility limitation (`docs/EVALUATION_CLOSURE_MATRIX.md` items 9, 11, 13, 22; `docs/DECISIONS.md` ADR-001), not silently treated as equivalent to what H2 provides.
- This decision was made by the engineer, not defaulted to by the AI.

**Sign-off:** Given directly by the engineer as an explicit instruction, which is the decision itself — no separate approval step applies.

---

## [2026-08-20] Phase 1 implementation — Project Foundation + Scenario A Core Domain

**Prompt intent:** Implement Phase 1 only (explicitly scoped: project foundation, layered architecture, the audit event write/query APIs, SHA-256 hash chain, chain verification, concurrency control for the write path, forward-declared security interfaces) — not Scenario B, not Scenario C, not the external chain anchor. Update `docs/ARCHITECTURE.md`, `docs/REQUIREMENTS.md`, `docs/IMPLEMENTATION_PLAN.md`, `docs/SECURITY.md`, `docs/TESTING.md`, `docs/ENDPOINT_TEST_MATRIX.md`, `docs/DECISIONS.md`, and this log. Explicit instruction: run tests, inspect failures, fix failures, inspect the diff, check for secrets, then make one meaningful commit — and not to fabricate validation that hadn't happened.

**AI output:** A full Spring Boot project (pom.xml; entity/repository/hash/domain/dto/service/controller/exception/security packages; a Flyway migration; 24 tests). Before any of that, the AI discovered this environment had no usable JDK (only JDK 10 from 2020) and no Maven, and installed a JDK 21 (via `winget`) and Maven 3.9.9 (downloaded from the Apache archive) rather than writing code that could not actually be compiled or tested — flagged to the engineer as a system-level change before proceeding.

**What was generated and then corrected during this session (AI-authored, AI-caught, not engineer-caught after the fact):**
1. `mvn compile` succeeded on the first attempt (25 source files). `mvn test` did not: `LocalServerPort` was imported from the wrong package for this Spring Boot version (`org.springframework.boot.web.server` instead of `org.springframework.boot.test.web.server`) — a version-specific API detail the AI got wrong initially and fixed after the compiler reported it.
2. Hibernate schema-validation failures: the JPA entity's inferred column types (`VARCHAR(255)` default for a converted `String` attribute, then a `columnDefinition="CLOB"` attempt) didn't match what Flyway had actually created (`CLOB`, `CHAR(64)`). Fixed by switching to `@Lob` for the payload converter and `VARCHAR(64)` (instead of `CHAR(64)`) for the hash columns in the migration — found only by actually running the build, not by inspection.
3. A real logic bug: `TamperDetectionTest.detectsIncorrectPreviousHash` failed because `ChainVerificationService` reported `CONTENT_MISMATCH` instead of the expected `LINKAGE_BROKEN` when `previous_hash` was tampered directly. Root cause: `previous_hash` is itself one of the fields that feeds `record_hash`'s own computation, so tampering it also breaks the content-hash recomputation. Fixed by checking linkage before content in the verification loop, with the reasoning recorded in `docs/ARCHITECTURE.md` and `docs/DECISIONS.md` rather than just silently reordered.
4. A real concurrency-adjacent bug, not a concurrency-control bug: `ConcurrentAppendTest` initially failed with **every** one of 20 records reporting `CONTENT_MISMATCH`, even though sequencing was perfectly correct (20 unique, contiguous sequence numbers — proving the pessimistic-lock mechanism itself worked). Root cause: `OffsetDateTime.now()` (used only in this test) carries nanosecond precision, which the database round-trip did not preserve exactly, so the hash computed at write time (full precision) didn't match the hash recomputed at verify time (post-round-trip precision) — a false-tamper signal with nothing actually tampered. Fixed by truncating timestamps to millisecond precision *before* hashing, in `AuditEventService`, so the hashed value and the persisted/re-read value are always identical regardless of what the database's real storage precision turns out to be. Recorded in `docs/DECISIONS.md` (ADR-002 addendum) as a genuine implementation-time finding, not designed for up front.

**Human decision:** The engineer directed Phase 1's exact scope and explicitly required that tests actually be run and failures actually be fixed before any commit — this shaped the AI's process (build-then-fix-then-rebuild, not write-once-and-claim-done). The four corrections above were made by the AI in response to real, observed failures (compiler errors, Hibernate startup errors, failing assertions), not engineer-directed code review; the engineer had not reviewed the resulting source code line-by-line as of the original version of this entry.

**Validation performed:** `mvn test` → `Tests run: 24, Failures: 0, Errors: 0, Skipped: 0` (BUILD SUCCESS). `mvn clean package` → BUILD SUCCESS (packaged jar). A live manual run: the packaged jar started standalone, two events written via `curl`, `/audit/verify` confirmed intact, a record tampered directly via `org.h2.tools.Shell` (bypassing the application), `/audit/verify` re-run and correctly reported the tamper (`CONTENT_MISMATCH`, correct `sequenceNo` and `recordId`).

**Tests run:** All 24 (9 unit, 15 integration) — see `docs/TESTING.md` for the full mapping to test classes/methods and the exact commands used.

**Human approval:** Originally logged as pending in the same turn the work was produced. The engineer's next message ("what is phase 2?") implicitly treated Phase 1 as complete and moved the conversation forward without raising an objection to the implementation; the engineer then separately flagged that the whole of Phase 1 had landed in a single commit rather than one commit per milestone, which the AI agreed was a real process gap (see the history-reconstruction entry below) and corrected. This is not the same as a line-by-line code review sign-off, which still has not explicitly occurred as of this entry.

**Git commit:** Originally `9d18f0a` (single commit for all of Phase 1). Superseded — see the next entry.

---

## [2026-08-20] Git history reconstruction: Phase 1 mega-commit split into per-milestone commits

**Prompt intent:** The engineer presented a milestone-based roadmap (Planning → Project Structure → Milestones 1-6 → Security → Redaction → Retention/Export → Compliance → Security/negative testing → JaCoCo/CI) and asked where the project currently stood against it, explicitly restating that "on every meaningful step we have to do commits."

**AI output:** Pointed out, unprompted, that all of Planning/Structure/Milestones 1-6 existed as a single commit (`9d18f0a`) despite being functionally complete — directly contradicting the stated per-milestone commit discipline and the assignment's own requirement for real incremental history. Proposed two options (rewrite history now, while nothing is pushed anywhere and it's still free to do; or accept the single commit and be disciplined from here on) and asked the engineer to choose rather than deciding unilaterally.

**Human decision:** The engineer chose to rewrite history now. The AI then reconstructed the single commit into 9 sequential commits (Planning; Project Structure; Milestones 1-6; this entry's own wrap-up commit) using `git update-ref -d` to un-commit while preserving the working tree (verified safe beforehand: no remote configured, nothing pushed, single local branch). Two files that legitimately span multiple milestones (`AI_USAGE_LOG.md`, `AuditEventController.java`) were temporarily trimmed to their true point-in-time content and restored later, rather than landing in one commit out of order. `docs/REQUIREMENTS.md`, `docs/ARCHITECTURE.md`, and `docs/DECISIONS.md` were **not** split by paragraph-level history — they already contained Phase-1 status annotations added after the fact, and attempting to strip and re-add that content precisely was judged not worth the risk of introducing an error into documents that other commits' messages reference; this is stated explicitly in the Planning commit's message rather than left implicit.

**Sign-off:** Given directly by the engineer via the explicit choice above.

**Git commit:** See `git log` from this point forward — `9d18f0a` no longer exists as a ref (the objects remain reachable via reflog only, not referenced by any branch).

---

## [2026-08-20] Milestone 7: security / tenant authorization

**Prompt intent:** Implement all remaining roadmap milestones starting with Security/tenant authorization, committing at each meaningful step, and tracking status against `docs/EVALUATION_CLOSURE_MATRIX.md`.

**AI output:** JWT authentication (HS256, custom `OncePerRequestFilter`, not the full Spring Security starter — see `docs/DECISIONS.md` ADR-008 for why) and tenant/role authorization (ADR-012): `tenant_id` added to `audit_record` and to the hash input, derived only from the JWT (never the request body); query/fetch scoped to the caller's tenant unless `ROLE_AUDITOR`; `/audit/verify` gated to `ROLE_AUDITOR`. A `POST /dev/auth/token` endpoint issues tokens with zero identity verification — flagged repeatedly, in code comments and three separate docs, as not a real authentication endpoint, since it's the kind of thing that's easy to forget matters once the demo works. 19 new tests (unit + integration), all existing tests updated to attach auth headers.

**Bug found and fixed:** `AuditQueryService.search()`'s cursor computation used `page.isEmpty() ? afterSequenceNo : page.get(...).getSequenceNo()` — a `Long ? : long` ternary, which Java auto-unboxes even on the selected `Long` branch, throwing NPE the instant a query legitimately returned zero rows with a null cursor. No test before `TenantIsolationTest.queryIgnoresAttemptToRequestAnotherTenantAsNonAuditor` had ever produced a truly empty result set, so this pre-existing (Phase 1) latent bug went undetected until this milestone's new test surfaced it. Fixed with an explicit if/else, which doesn't have the pitfall.

**Human decision:** The engineer set the milestone order and the "commit on every meaningful step" requirement; the AI chose the specific implementation (JWT over API-key, custom filter over Spring Security, tenant-as-hashed-column, 404-not-403 for cross-tenant fetch, verify gated by role rather than tenant-scoped) and documented the reasoning and alternatives in ADR-008/ADR-012 for the engineer to review. Not yet reviewed line-by-line as of this entry.

**Validation performed:** `mvn test` → `Tests run: 43, Failures: 0, Errors: 0` (BUILD SUCCESS). Live run: two tenants issued tokens via `/dev/auth/token`; cross-tenant query returned `{"items":[]}`; `/audit/verify` returned 403 for `ROLE_USER` and 200 (`chainIntact: true`) for `ROLE_AUDITOR`.

**Human approval:** Pending as of this entry.

**Git commit:** See `git log` — Milestone 7 commit follows this entry's own commit.
