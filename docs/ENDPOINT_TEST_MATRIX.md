# Endpoint Test Matrix — Audit Log Service

Formal endpoint-to-requirement-to-test mapping (`docs/EVALUATION_CLOSURE_MATRIX.md` item 19, `TEST-01`). Extended with a coverage column once JaCoCo is wired in (item 2, `TEST-09`) — not yet available, so that column is omitted for now rather than filled with a placeholder number.

| Endpoint | Method | Requirement(s) | Test class(es) | Auth required? | Status |
|---|---|---|---|---|---|
| `/audit/events` | POST | FR-A1 | `AuditEventCreationTest` | No (Phase 1 gap — `docs/SECURITY.md`) | IMPLEMENTED, TESTED |
| `/audit/events` | GET | FR-A2 | `AuditEventQueryTest` | No (Phase 1 gap) | IMPLEMENTED, TESTED |
| `/audit/events/{id}` | GET | FR-A2 | Exercised indirectly via `TamperDetectionTest` (fetches by id implicitly through created responses); no dedicated single-record-fetch test yet | No (Phase 1 gap) | IMPLEMENTED, NOT independently tested |
| `/audit/verify` | GET | FR-A3, FR-A4 | `ChainIntegrityTest`, `TamperDetectionTest`, `ConcurrentAppendTest` | No (Phase 1 gap) | IMPLEMENTED, TESTED |
| `/audit/verify?fromSeq=&toSeq=` | GET | (secondary, optional) | none | No | NOT IMPLEMENTED |
| Archival trigger | (admin/scheduled) | FR-B1 | none | N/A | NOT IMPLEMENTED |
| `/audit/events/{id}/redact` (indicative) | POST | FR-B2 | none | N/A | NOT IMPLEMENTED |
| `/audit/export` | GET | FR-B3 | none | N/A | NOT IMPLEMENTED |
| `/audit/compliance-report` (indicative) | GET | FR-C2 | none | N/A | NOT IMPLEMENTED |

## Known gap in this matrix

`GET /audit/events/{id}` has no dedicated test asserting a 404 for an unknown id, and no dedicated success-path test isolated from the other test classes. This is a real, small coverage gap, noted here rather than silently left implicit — a reasonable Phase 2 cleanup item, not urgent enough to have blocked Phase 1 sign-off.

## Cross-reference

See `docs/TESTING.md` for the full mapping of the 15 required Phase 1 test scenarios to test methods, and `docs/EVALUATION_CLOSURE_MATRIX.md` items 17-26 for how this matrix and remaining test-coverage gaps map back to the previous evaluation's findings.
