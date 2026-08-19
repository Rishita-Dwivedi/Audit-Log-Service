package com.auditlog.export;

import com.auditlog.dto.AuditEventResponse;
import com.auditlog.dto.ExportBundleResponse;
import com.auditlog.dto.ExportChainContext;
import com.auditlog.dto.ExportSignature;
import com.auditlog.entity.AuditRecordEntity;
import com.auditlog.exception.BadRequestException;
import com.auditlog.repository.AuditRecordRepository;
import com.auditlog.security.AuditSecurityContext;
import com.auditlog.security.AuthenticatedPrincipal;
import com.auditlog.security.Roles;
import com.auditlog.service.AuditRecordMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.stream.Collectors;

/**
 * docs/REQUIREMENTS.md FR-B3, docs/DECISIONS.md ADR-013. Builds a self-contained, verifiable
 * bundle: a recipient with no access to this server can (a) recompute each record's own hash
 * from its content, (b) walk the linkage within the bundle, (c) verify the manifest signature
 * using only the published public key. See ExportSigningService for the signing/trust model.
 */
@Service
public class ExportBundleService {

    private final AuditRecordRepository auditRecordRepository;
    private final ExportSigningService exportSigningService;

    public ExportBundleService(AuditRecordRepository auditRecordRepository, ExportSigningService exportSigningService) {
        this.auditRecordRepository = auditRecordRepository;
        this.exportSigningService = exportSigningService;
    }

    @Transactional(readOnly = true)
    public ExportBundleResponse export(String requestedTenantId, String actorId, String resourceId) {
        if ((actorId == null || actorId.isBlank()) && (resourceId == null || resourceId.isBlank())) {
            throw new BadRequestException("At least one of actorId or resourceId is required");
        }

        AuthenticatedPrincipal principal = AuditSecurityContext.current();
        String tenantId = principal.hasRole(Roles.AUDITOR) && requestedTenantId != null
                ? requestedTenantId : principal.tenantId();

        List<AuditRecordEntity> entities = auditRecordRepository.findForExport(tenantId, actorId, resourceId);
        List<AuditEventResponse> records = entities.stream().map(AuditRecordMapper::toResponse).collect(Collectors.toList());

        OffsetDateTime exportedAt = OffsetDateTime.now();
        int recordCount = records.size();

        long firstSequenceNo = entities.isEmpty() ? 0 : entities.get(0).getSequenceNo();
        long lastSequenceNo = entities.isEmpty() ? 0 : entities.get(entities.size() - 1).getSequenceNo();
        String hashOfLastRecordBeforeRange = entities.isEmpty() ? "" : entities.get(0).getPreviousHash();
        ExportChainContext chainContext = new ExportChainContext(firstSequenceNo, lastSequenceNo, hashOfLastRecordBeforeRange);

        String canonicalManifest = canonicalManifest(exportedAt, tenantId, recordCount, chainContext, records);
        String signatureValue = exportSigningService.sign(canonicalManifest);
        ExportSignature signature = new ExportSignature(
                ExportSigningService.ALGORITHM, exportSigningService.publicKeyBase64(), signatureValue);

        return new ExportBundleResponse(exportedAt, tenantId, recordCount, records, chainContext, signature);
    }

    /**
     * Reproducible from the bundle's own JSON fields alone -- a recipient never needs to call
     * back into this server to reconstruct the same string and verify the signature.
     */
    public static String canonicalManifest(OffsetDateTime exportedAt, String tenantId, int recordCount,
                                            ExportChainContext chainContext, List<AuditEventResponse> records) {
        StringBuilder sb = new StringBuilder();
        sb.append(exportedAt.toInstant()).append('|')
                .append(tenantId).append('|')
                .append(recordCount).append('|')
                .append(chainContext.firstSequenceNo()).append('|')
                .append(chainContext.lastSequenceNo()).append('|')
                .append(chainContext.hashOfLastRecordBeforeRange());
        for (AuditEventResponse record : records) {
            sb.append('|').append(record.recordHash());
        }
        return sb.toString();
    }
}
