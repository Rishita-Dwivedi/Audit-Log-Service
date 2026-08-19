package com.auditlog.dto;

public record VerifyResponse(
        boolean chainIntact,
        long recordsChecked,
        ViolationDetail firstViolation,
        int additionalViolations
) {
}
