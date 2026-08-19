# Audit Log Service

A tamper-evident audit log service (Secure Audit Log Service assignment). All 12 planned milestones are implemented — see `docs/IMPLEMENTATION_PLAN.md` and `ENGINEERING_SUMMARY.md` for full status.

## Prerequisites

- Java 21
- Maven (or use `mvn` if already on your `PATH`)
- No Docker / external services required — persistence is H2, embedded (`docs/DECISIONS.md` ADR-001).

## Run the tests

```
mvn test
```

Expected: `Tests run: 84, Failures: 0, Errors: 0`. Coverage report at `target/site/jacoco/index.html` after the run.

## Build and run the service

```
mvn clean package
java -jar target/audit-log-service-0.1.0-SNAPSHOT.jar
```

The service starts on `http://localhost:8080`, using a file-based H2 database at `./data/auditlog` (created on first run, gitignored).

## Try it — Swagger UI (recommended for a live demo)

Open **`http://localhost:8080/swagger-ui/index.html`**.

1. Get a token: expand `POST /dev/auth/token`, "Try it out", body `{"subjectId":"demo","tenantId":"tenant-1","roles":["ROLE_AUDITOR"]}`, Execute. Copy `accessToken` from the response.
2. Click **Authorize** (top right), paste the token (without "Bearer "), Authorize, Close.
3. Every other endpoint now works via "Try it out" with that token attached automatically.

**Every endpoint (except `/dev/auth/token`, `/actuator/health`, and Swagger's own pages) requires a valid JWT.** `/dev/auth/token` is explicitly **not** a real authentication endpoint — see `docs/SECURITY.md`.

## Try it — curl

```
TOKEN=$(curl -s -X POST http://localhost:8080/dev/auth/token -H "Content-Type: application/json" \
  -d '{"subjectId":"demo","tenantId":"tenant-1","roles":["ROLE_AUDITOR"]}' | jq -r .accessToken)

curl -X POST http://localhost:8080/audit/events -H "Authorization: Bearer $TOKEN" -H "Content-Type: application/json" \
  -d '{"eventType":"USER_LOGIN","actorId":"user-1","resourceType":"ACCOUNT","resourceId":"acct-1","payload":{"ip":"127.0.0.1"},"timestamp":"2026-08-20T00:00:00Z"}'

curl http://localhost:8080/audit/events -H "Authorization: Bearer $TOKEN"
curl http://localhost:8080/audit/verify -H "Authorization: Bearer $TOKEN"
```

To see tamper detection: stop the app, open `./data/auditlog.mv.db` with the H2 shell (`java -cp <path-to-h2-jar> org.h2.tools.Shell -url "jdbc:h2:file:./data/auditlog"`), run a raw `UPDATE audit_record SET payload = '...' WHERE sequence_no = 1;`, then hit `GET /audit/verify` again — it reports `chainIntact: false` with the tampered record identified (this is exactly what `TamperDetectionTest` automates, and what's been run live repeatedly — see `docs/TESTING.md`).

## Documentation

- `docs/REQUIREMENTS.md` — normalized requirements, Scenario A/B/C, acceptance criteria.
- `docs/ARCHITECTURE.md` — components, API design, data model, hash-chain/verification/redaction/export approach.
- `docs/DECISIONS.md` — 14 ADRs: why H2, why this concurrency mechanism, why this redaction/export design, etc.
- `docs/scenario-c.md` — Scenario C clarification (written before any Scenario C code).
- `docs/IMPLEMENTATION_PLAN.md` — milestone-by-milestone build status.
- `docs/SECURITY.md` — current security posture and honest gaps.
- `docs/TESTING.md`, `docs/ENDPOINT_TEST_MATRIX.md` — test strategy, results, coverage.
- `docs/EVALUATION_CLOSURE_MATRIX.md` — tracking closure of a previous evaluation's 26 findings.
- `ENGINEERING_SUMMARY.md` — final plan/rationale/risks/limitations summary.
- `AI_USAGE_LOG.md` — AI usage traceability.
- `ATTESTATION.md` — submission attestation.
