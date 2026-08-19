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
