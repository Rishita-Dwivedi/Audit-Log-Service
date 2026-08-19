package com.auditlog.dto;

import java.time.OffsetDateTime;
import java.util.List;

public record ExportBundleResponse(
        OffsetDateTime exportedAt,
        String tenantId,
        int recordCount,
        List<AuditEventResponse> records,
        ExportChainContext chainContext,
        ExportSignature signature
) {
}
