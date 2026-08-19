# Testing — Audit Log Service

## How to run

```
mvn test              # run the full test suite
mvn clean package      # build + test + package the runnable jar
```

No Docker, no external services, no manual setup beyond a JDK 21 + Maven — H2 is embedded (`docs/DECISIONS.md` ADR-001).

## Result (2026-08-20, Phase 1)

```
Tests run: 24, Failures: 0, Errors: 0, Skipped: 0
BUILD SUCCESS
```

This is the actual output of running `mvn test` against the Phase 1 codebase, not an aspirational target. `mvn clean package` also succeeds and produces a runnable jar (`target/audit-log-service-0.1.0-SNAPSHOT.jar`).

## Strategy

- **Unit tests** (`com.auditlog.hash`, `com.auditlog.controller.AppendOnlyApiTest`): pure JVM, no Spring context, no database. Cover canonicalization determinism and hash-chain math directly.
- **Integration tests** (everything under `com.auditlog` extending `AbstractApiIntegrationTest`): `@SpringBootTest(webEnvironment = RANDOM_PORT)` + `TestRestTemplate`, exercising the real HTTP stack against a real (in-memory) H2 database. Direct-data-store tampering is simulated with an autowired `JdbcTemplate`, bypassing the application entirely — matching the assignment's own validation method.

## The 15 required Phase 1 test scenarios, mapped to what actually exists

| # | Scenario | Test class.method | Type |
|---|---|---|---|
| 1 | Successful event creation | `AuditEventCreationTest.createsEventSuccessfully` | Integration |
| 2 | Validation failure | `AuditEventCreationTest.rejectsRequestMissingRequiredFields` | Integration |
| 3 | Query by `actorId` | `AuditEventQueryTest.filtersByActorId` | Integration |
| 4 | Query by `resourceType`/`resourceId` | `AuditEventQueryTest.filtersByResourceTypeAndResourceId` | Integration |
| 5 | Query by `eventType` | `AuditEventQueryTest.filtersByEventType` | Integration |
| 6 | Query by time range | `AuditEventQueryTest.filtersByTimeRange` | Integration |
| 7 | Pagination | `AuditEventQueryTest.paginatesUsingCursor` | Integration |
| 8 | First record uses genesis value | `ChainIntegrityTest.firstRecordUsesGenesisValue` | Integration |
| 9 | Second record points to first hash | `ChainIntegrityTest.secondRecordPointsToFirstRecordsHash` | Integration |
| 10 | Chain verification succeeds | `ChainIntegrityTest.verificationSucceedsForAnUntamperedChain` | Integration |
| 11 | Direct record modification detected | `TamperDetectionTest.detectsDirectContentModification` | Integration |
| 12 | Incorrect `previousHash` detected | `TamperDetectionTest.detectsIncorrectPreviousHash` | Integration |
| 13 | Sequence gap detected | `TamperDetectionTest.detectsSequenceGapFromDeletedMiddleRecord` | Integration |
| 14 | Deleted middle record detected | `TamperDetectionTest.deletedMiddleRecordProducesBothMissingRecordAndLinkageViolations` | Integration |
| 15 | Concurrent appends do not break the chain | `ConcurrentAppendTest.concurrentAppendsDoNotBreakTheChain` | Integration |

Plus, beyond the required 15: `PayloadCanonicalizerTest` (4 tests), `HashChainServiceTest` (4 tests), and `AppendOnlyApiTest` (1 test), all unit-level, covering the hash-chain core in isolation from the database.

## Live manual demonstration (in addition to automated tests)

The assignment's validation path was also run once, live, outside the automated suite: the packaged jar was started, two events were written via `curl`, `/audit/verify` confirmed the chain intact, a record was then tampered directly in the H2 data file via `org.h2.tools.Shell` (a raw `UPDATE`, not going through the application), and `/audit/verify` was called again — it correctly reported `chainIntact: false` with the tampered record's exact `sequenceNo`, `recordId`, and `CONTENT_MISMATCH` violation type.

## Concurrency test scope (explicit, not glossed over)

`ConcurrentAppendTest` fires 20 writers from separate threads within a **single** application instance (a `CountDownLatch` releases them together to maximize contention on the `chain_head` lock). This is concurrent-thread contention, not literal multi-process/multi-instance contention. `docs/EVALUATION_CLOSURE_MATRIX.md` item 12 tracks this distinction explicitly: the underlying locking mechanism (a database-level row lock, not an in-JVM lock) should generalize to multiple application instances sharing the same database, but that has not been tested with an actual second process.

## What is explicitly NOT covered by Phase 1 testing

- Database fault injection / transaction rollback under a forced mid-transaction failure (`docs/EVALUATION_CLOSURE_MATRIX.md` item 11) — not implemented this phase.
- Authentication/authorization negative tests (items 14/16) — nothing to test yet; no auth exists.
- Malformed/boundary edge cases beyond the basic "missing required field" case (item 20, `TEST-03`) — e.g., oversized payloads, unusual Unicode, extreme pagination values.
- JaCoCo line/branch/method/class coverage measurement (item 2, `TEST-09`) — not yet wired into the build; "24/24 passing" is a test-count fact, not a coverage percentage, and the two should not be conflated.
- Load/performance testing beyond the informal timing observed while running the suite locally.
- Scenario B/C tests — nothing exists yet to test.

## Reproducibility

`mvn test` and `mvn clean package` were both run against this exact codebase on 2026-08-20 in this environment (Java 21, Maven 3.9.9, no Docker). Docker/Testcontainers-based reproducibility evidence (`docs/EVALUATION_CLOSURE_MATRIX.md` items 11, 22) is out of scope given the H2-only decision (`docs/DECISIONS.md` ADR-001) and is not claimed here.
