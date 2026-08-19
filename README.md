# Audit Log Service

A tamper-evident audit log service (Secure Audit Log Service assignment). Phase 1 (Project Foundation + Scenario A Core Domain) is implemented — see `docs/IMPLEMENTATION_PLAN.md` for status.

## Prerequisites

- Java 21
- Maven (or use `mvn` if already on your `PATH`)
- No Docker / external services required — persistence is H2, embedded (`docs/DECISIONS.md` ADR-001).

## Run the tests

```
mvn test
```

Expected: `Tests run: 24, Failures: 0, Errors: 0`.

## Build and run the service

```
mvn clean package
java -jar target/audit-log-service-0.1.0-SNAPSHOT.jar
```

The service starts on `http://localhost:8080`. It uses a file-based H2 database at `./data/auditlog` (created on first run, gitignored).

## Try it

```
curl -X POST http://localhost:8080/audit/events \
  -H "Content-Type: application/json" \
  -d '{"eventType":"USER_LOGIN","actorId":"user-1","resourceType":"ACCOUNT","resourceId":"acct-1","payload":{"ip":"127.0.0.1"},"timestamp":"2026-08-20T00:00:00Z"}'

curl http://localhost:8080/audit/events

curl http://localhost:8080/audit/verify
```

To see tamper detection: stop the app, open `./data/auditlog.mv.db` with the H2 shell (`java -cp <path-to-h2.jar> org.h2.tools.Shell -url "jdbc:h2:file:./data/auditlog"`), run a raw `UPDATE audit_record SET payload = '...' WHERE sequence_no = 1;`, then hit `GET /audit/verify` again — it will report `chainIntact: false` with the tampered record identified. (This is exactly what `TamperDetectionTest` does automatically, and what was run live during Phase 1 — see `docs/TESTING.md`.)

## Documentation

- `docs/REQUIREMENTS.md` — normalized requirements, Scenario A/B/C, acceptance criteria.
- `docs/ARCHITECTURE.md` — components, API design, data model, hash-chain/verification approach.
- `docs/DECISIONS.md` — ADRs: why H2, why this concurrency mechanism, why this redaction design, etc.
- `docs/IMPLEMENTATION_PLAN.md` — phase-by-phase build status.
- `docs/SECURITY.md` — current security posture (Phase 1: no auth enforced — read this before assuming otherwise).
- `docs/TESTING.md` — test strategy and results.
- `docs/ENDPOINT_TEST_MATRIX.md` — endpoint-to-requirement-to-test mapping.
- `docs/EVALUATION_CLOSURE_MATRIX.md` — tracking closure of a previous evaluation's findings.
- `AI_USAGE_LOG.md` — AI usage traceability.
- `ATTESTATION.md` — submission attestation.
