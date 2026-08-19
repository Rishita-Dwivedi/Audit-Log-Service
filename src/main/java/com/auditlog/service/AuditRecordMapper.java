package com.auditlog.service;

import com.auditlog.dto.AuditEventResponse;
import com.auditlog.entity.AuditRecordEntity;

import java.util.ArrayList;
import java.util.List;

final class AuditRecordMapper {

    private AuditRecordMapper() {
    }

    static AuditEventResponse toResponse(AuditRecordEntity entity) {
        List<String> redactedFields = new ArrayList<>();
        entity.getRedactedFields().forEach(node -> redactedFields.add(node.asText()));

        return new AuditEventResponse(entity.getId(), entity.getSequenceNo(), entity.getTenantId(),
                entity.getEventType(), entity.getActorId(), entity.getResourceType(), entity.getResourceId(),
                entity.getPayload(), entity.getEventTimestamp(), entity.getRecordedAt(), entity.getRecordHash(),
                entity.getPreviousHash(), entity.getStatus().name(), redactedFields);
    }
}
