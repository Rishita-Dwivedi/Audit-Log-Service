package com.auditlog.dto;

/** wasReplay=true means an existing record with the same idempotency key was returned, not a new one. */
public record AppendResult(AuditEventResponse response, boolean wasReplay) {
}
