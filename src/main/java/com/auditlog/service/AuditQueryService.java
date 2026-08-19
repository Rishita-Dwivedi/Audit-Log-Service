package com.auditlog.service;

import com.auditlog.dto.AuditEventPageResponse;
import com.auditlog.dto.AuditEventResponse;
import com.auditlog.entity.AuditRecordEntity;
import com.auditlog.exception.ResourceNotFoundException;
import com.auditlog.repository.AuditRecordRepository;
import com.auditlog.security.AuditSecurityContext;
import com.auditlog.security.AuthenticatedPrincipal;
import com.auditlog.security.Roles;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Tenant isolation (docs/EVALUATION_CLOSURE_MATRIX.md item 3, SEC-03): a caller without the
 * AUDITOR role is always scoped to their own tenant, regardless of what they ask for -- any
 * tenantId they might attempt to pass is silently ignored rather than honored or rejected,
 * which avoids leaking whether other tenants' data exists at all. AUDITOR callers may narrow
 * to a specific tenant or omit it to see all tenants.
 */
@Service
public class AuditQueryService {

    private static final int DEFAULT_PAGE_SIZE = 50;
    private static final int MAX_PAGE_SIZE = 200;

    private final AuditRecordRepository auditRecordRepository;

    public AuditQueryService(AuditRecordRepository auditRecordRepository) {
        this.auditRecordRepository = auditRecordRepository;
    }

    public AuditEventPageResponse search(String requestedTenantId, String actorId, String resourceType,
                                          String resourceId, String eventType, OffsetDateTime from, OffsetDateTime to,
                                          Long afterSequenceNo, Integer pageSize) {
        AuthenticatedPrincipal principal = AuditSecurityContext.current();
        String effectiveTenantId = principal.hasRole(Roles.AUDITOR) ? requestedTenantId : principal.tenantId();

        int effectivePageSize = clampPageSize(pageSize);

        // Fetch one extra row to detect whether more results exist, avoiding a separate count query.
        List<AuditRecordEntity> rows = auditRecordRepository.search(effectiveTenantId, actorId, resourceType,
                resourceId, eventType, from, to, afterSequenceNo, PageRequest.of(0, effectivePageSize + 1));

        boolean hasMore = rows.size() > effectivePageSize;
        List<AuditRecordEntity> page = hasMore ? rows.subList(0, effectivePageSize) : rows;

        List<AuditEventResponse> items = page.stream().map(AuditRecordMapper::toResponse).collect(Collectors.toList());
        // Deliberately not a ternary: `cond ? Long : long` auto-unboxes the Long branch even
        // when it's the selected one, which throws NPE the moment afterSequenceNo is null and
        // page is empty (found via TenantIsolationTest -- no earlier test ever produced a truly
        // empty result set, so this never surfaced before).
        Long nextCursor;
        if (page.isEmpty()) {
            nextCursor = afterSequenceNo;
        } else {
            nextCursor = page.get(page.size() - 1).getSequenceNo();
        }

        return new AuditEventPageResponse(items, effectivePageSize, nextCursor, hasMore);
    }

    public AuditEventResponse findById(UUID id) {
        AuthenticatedPrincipal principal = AuditSecurityContext.current();
        AuditRecordEntity entity = auditRecordRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Audit record not found: " + id));

        // 404, not 403, for a cross-tenant hit: confirming a record exists for another tenant
        // is itself an information leak (docs/REQUIREMENTS.md NFR7; assignment Section 12).
        boolean owns = entity.getTenantId().equals(principal.tenantId());
        if (!owns && !principal.hasRole(Roles.AUDITOR)) {
            throw new ResourceNotFoundException("Audit record not found: " + id);
        }

        return AuditRecordMapper.toResponse(entity);
    }

    private int clampPageSize(Integer requested) {
        if (requested == null) {
            return DEFAULT_PAGE_SIZE;
        }
        return Math.max(1, Math.min(requested, MAX_PAGE_SIZE));
    }
}
