package com.auditlog.service;

import com.auditlog.dto.AuditEventPageResponse;
import com.auditlog.dto.AuditEventResponse;
import com.auditlog.entity.AuditRecordEntity;
import com.auditlog.exception.BadRequestException;
import com.auditlog.exception.ForbiddenException;
import com.auditlog.repository.AuditRecordRepository;
import com.auditlog.security.AuditSecurityContext;
import com.auditlog.security.AuthenticatedPrincipal;
import com.auditlog.security.Roles;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Scenario C (docs/scenario-c.md, docs/REQUIREMENTS.md FR-C1/FR-C2): a thin layer over the
 * existing query infrastructure, scoped to a configured "client account data" resource-type
 * allow-list, gated to ROLE_COMPLIANCE_OFFICER, with a mandatory (never defaulted) time range.
 */
@Service
public class ComplianceReportService {

    private static final int DEFAULT_PAGE_SIZE = 50;
    private static final int MAX_PAGE_SIZE = 200;

    private final AuditRecordRepository auditRecordRepository;
    private final List<String> clientDataResourceTypes;

    public ComplianceReportService(AuditRecordRepository auditRecordRepository,
                                    @Value("${audit.compliance.client-data-resource-types}") String resourceTypesCsv) {
        this.auditRecordRepository = auditRecordRepository;
        this.clientDataResourceTypes = Arrays.stream(resourceTypesCsv.split(","))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public AuditEventPageResponse generateReport(String requestedTenantId, OffsetDateTime from, OffsetDateTime to,
                                                  Long afterSequenceNo, Integer pageSize) {
        AuthenticatedPrincipal principal = AuditSecurityContext.current();
        if (!principal.hasRole(Roles.COMPLIANCE_OFFICER)) {
            throw new ForbiddenException("GET /audit/compliance-report requires the COMPLIANCE_OFFICER role");
        }
        // Deliberately never defaulted (docs/scenario-c.md): an implicit time range risks an
        // incomplete report appearing complete to whoever requested it.
        if (from == null || to == null) {
            throw new BadRequestException("Both 'from' and 'to' are required for a compliance report");
        }

        // COMPLIANCE_OFFICER is cross-tenant capable like AUDITOR: an explicit tenantId narrows
        // to one tenant, omitting it reports across all tenants.
        int effectivePageSize = clampPageSize(pageSize);
        List<AuditRecordEntity> rows = auditRecordRepository.findForComplianceReport(
                requestedTenantId, clientDataResourceTypes, from, to, afterSequenceNo,
                PageRequest.of(0, effectivePageSize + 1));

        boolean hasMore = rows.size() > effectivePageSize;
        List<AuditRecordEntity> page = hasMore ? rows.subList(0, effectivePageSize) : rows;
        List<AuditEventResponse> items = page.stream().map(AuditRecordMapper::toResponse).collect(Collectors.toList());

        Long nextCursor;
        if (page.isEmpty()) {
            nextCursor = afterSequenceNo;
        } else {
            nextCursor = page.get(page.size() - 1).getSequenceNo();
        }

        return new AuditEventPageResponse(items, effectivePageSize, nextCursor, hasMore);
    }

    private int clampPageSize(Integer requested) {
        if (requested == null) {
            return DEFAULT_PAGE_SIZE;
        }
        return Math.max(1, Math.min(requested, MAX_PAGE_SIZE));
    }
}
