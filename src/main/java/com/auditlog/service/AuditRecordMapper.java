package com.auditlog.service;

import com.auditlog.dto.AuditEventResponse;
import com.auditlog.entity.AuditRecordEntity;

final class AuditRecordMapper {

    private AuditRecordMapper() {
    }

    static AuditEventResponse toResponse(AuditRecordEntity entity) {
        return new AuditEventResponse(entity.getId(), entity.getSequenceNo(), entity.getTenantId(),
                entity.getEventType(), entity.getActorId(), entity.getResourceType(), entity.getResourceId(),
                entity.getPayload(), entity.getEventTimestamp(), entity.getRecordedAt(), entity.getRecordHash(),
                entity.getPreviousHash());
    }
}
