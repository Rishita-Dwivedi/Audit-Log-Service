package com.auditlog.service;

import com.auditlog.dto.RetentionApplyResponse;
import com.auditlog.entity.AuditRecordEntity;
import com.auditlog.exception.ForbiddenException;
import com.auditlog.repository.AuditRecordRepository;
import com.auditlog.security.AuditSecurityContext;
import com.auditlog.security.AuthenticatedPrincipal;
import com.auditlog.security.Roles;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.List;

/**
 * docs/DECISIONS.md ADR-004: soft-delete via a status flag, based on recordedAt (server truth)
 * rather than the caller-supplied eventTimestamp -- otherwise a caller could game retention
 * eligibility by submitting an old timestamp. No scheduler (the assignment doesn't require
 * one) -- a manual, AUDITOR-only admin endpoint triggers archival.
 */
@Service
public class RetentionService {

    private final AuditRecordRepository auditRecordRepository;
    private final int defaultWindowDays;

    public RetentionService(AuditRecordRepository auditRecordRepository,
                             @Value("${audit.retention.window-days}") int defaultWindowDays) {
        this.auditRecordRepository = auditRecordRepository;
        this.defaultWindowDays = defaultWindowDays;
    }

    @Transactional
    public RetentionApplyResponse applyRetention(Integer windowDaysOverride) {
        AuthenticatedPrincipal principal = AuditSecurityContext.current();
        if (!principal.hasRole(Roles.AUDITOR)) {
            throw new ForbiddenException("POST /audit/retention/apply requires the AUDITOR role");
        }

        int windowDays = windowDaysOverride != null ? windowDaysOverride : defaultWindowDays;
        OffsetDateTime cutoff = OffsetDateTime.now().minusDays(windowDays);

        List<AuditRecordEntity> eligible = auditRecordRepository.findActiveOlderThan(cutoff);
        eligible.forEach(AuditRecordEntity::archive);

        return new RetentionApplyResponse(eligible.size(), windowDays);
    }
}
