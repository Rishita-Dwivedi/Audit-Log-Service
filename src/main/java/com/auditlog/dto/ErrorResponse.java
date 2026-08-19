package com.auditlog.dto;

import java.time.OffsetDateTime;
import java.util.List;

public record ErrorResponse(
        OffsetDateTime timestamp,
        int status,
        String error,
        List<String> details
) {
}
