package com.auditlog.dto;

import com.auditlog.domain.ViolationType;

public record ViolationDetail(
        long sequenceNo,
        String recordId,
        ViolationType violationType,
        String detail
) {
}
