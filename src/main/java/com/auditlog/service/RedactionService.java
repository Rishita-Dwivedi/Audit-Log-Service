package com.auditlog.service;

import com.auditlog.dto.AuditEventResponse;
import com.auditlog.entity.AuditRecordEntity;
import com.auditlog.exception.BadRequestException;
import com.auditlog.exception.ResourceNotFoundException;
import com.auditlog.redaction.RedactionCommitmentService;
import com.auditlog.repository.AuditRecordRepository;
import com.auditlog.security.AuditSecurityContext;
import com.auditlog.security.AuthenticatedPrincipal;
import com.auditlog.security.Roles;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * docs/DECISIONS.md ADR-003 (mechanism) and ADR-012 (authorization, applied the same way as
 * query/fetch -- tenant-scoped unless AUDITOR, 404 not 403 for cross-tenant).
 */
@Service
public class RedactionService {

    private final AuditRecordRepository auditRecordRepository;
    private final RedactionCommitmentService redactionCommitmentService;

    public RedactionService(AuditRecordRepository auditRecordRepository,
                             RedactionCommitmentService redactionCommitmentService) {
        this.auditRecordRepository = auditRecordRepository;
        this.redactionCommitmentService = redactionCommitmentService;
    }

    @Transactional
    public AuditEventResponse redact(UUID id, List<String> fields, String reason) {
        AuthenticatedPrincipal principal = AuditSecurityContext.current();
        AuditRecordEntity entity = auditRecordRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Audit record not found: " + id));

        boolean owns = entity.getTenantId().equals(principal.tenantId());
        if (!owns && !principal.hasRole(Roles.AUDITOR)) {
            throw new ResourceNotFoundException("Audit record not found: " + id);
        }

        Set<String> knownFields = new java.util.HashSet<>();
        entity.getFieldCommitments().fieldNames().forEachRemaining(knownFields::add);
        for (String field : fields) {
            if (!knownFields.contains(field)) {
                throw new BadRequestException("Unknown payload field: '" + field
                        + "' -- only top-level fields present at write time can be redacted");
            }
        }

        Map<String, String> tombstones = new LinkedHashMap<>();
        for (String field : fields) {
            String commitment = entity.getFieldCommitments().get(field).asText();
            tombstones.put(field, redactionCommitmentService.tombstone(commitment));
        }

        entity.applyRedaction(tombstones, principal.subjectId(), OffsetDateTime.now());

        return AuditRecordMapper.toResponse(entity);
    }
}
