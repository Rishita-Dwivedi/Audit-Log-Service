package com.auditlog.dto;

import java.util.List;

/**
 * Keyset ("cursor") pagination on sequence_no, not offset -- see docs/ARCHITECTURE.md.
 * Pass nextCursor back as afterSequenceNo to fetch the following page.
 */
public record AuditEventPageResponse(
        List<AuditEventResponse> items,
        int pageSize,
        Long nextCursor,
        boolean hasMore
) {
}
