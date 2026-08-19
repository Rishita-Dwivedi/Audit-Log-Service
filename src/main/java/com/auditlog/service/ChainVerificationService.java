package com.auditlog.service;

import com.auditlog.domain.ViolationType;
import com.auditlog.dto.VerifyResponse;
import com.auditlog.dto.ViolationDetail;
import com.auditlog.entity.AuditRecordEntity;
import com.auditlog.exception.ForbiddenException;
import com.auditlog.hash.HashChainService;
import com.auditlog.redaction.RedactionCommitmentService;
import com.auditlog.repository.AuditRecordRepository;
import com.auditlog.security.AuditSecurityContext;
import com.auditlog.security.AuthenticatedPrincipal;
import com.auditlog.security.Roles;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;

/**
 * Walks the chain in sequence_no order and independently recomputes each record's hash and
 * linkage. Known, documented limitation (docs/EVALUATION_CLOSURE_MATRIX.md item 14, ARC-02):
 * deleting the newest record(s) from the tail is NOT detectable here, since there is nothing
 * after the deleted tail to reveal a broken link. That requires an external chain-head anchor,
 * which is out of scope for Phase 1.
 *
 * Milestone 8: record_hash alone cannot catch a raw payload value being tampered without also
 * updating its commitment, since record_hash is computed from field_commitments, not the raw
 * payload (docs/DECISIONS.md ADR-003). RedactionCommitmentService.verifyFieldCommitments()
 * closes that gap by independently reconciling payload against field_commitments per record.
 */
@Service
public class ChainVerificationService {

    private final AuditRecordRepository auditRecordRepository;
    private final HashChainService hashChainService;
    private final RedactionCommitmentService redactionCommitmentService;

    public ChainVerificationService(AuditRecordRepository auditRecordRepository, HashChainService hashChainService,
                                     RedactionCommitmentService redactionCommitmentService) {
        this.auditRecordRepository = auditRecordRepository;
        this.hashChainService = hashChainService;
        this.redactionCommitmentService = redactionCommitmentService;
    }

    @Transactional(readOnly = true)
    public VerifyResponse verify() {
        // Only AUDITOR may see system-wide chain state (docs/EVALUATION_CLOSURE_MATRIX.md
        // item 3, SEC-03): the chain is global across all tenants, so this is deliberately not
        // tenant-scoped -- a tenant-scoped verify would misreport other tenants' records as
        // "missing" from a tenant's own sub-sequence, which is not a real integrity break.
        AuthenticatedPrincipal principal = AuditSecurityContext.current();
        if (!principal.hasRole(Roles.AUDITOR)) {
            throw new ForbiddenException("GET /audit/verify requires the AUDITOR role");
        }

        List<AuditRecordEntity> records = auditRecordRepository.findAllByOrderBySequenceNoAsc();
        List<ViolationDetail> violations = new ArrayList<>();

        AuditRecordEntity previous = null;
        for (AuditRecordEntity record : records) {
            // Linkage is checked before content on purpose: previous_hash is itself one of the
            // fields that feeds record_hash's own computation (see HashChainService), so
            // directly tampering previous_hash also makes the content-hash recomputation fail.
            // Checking linkage first reports the more specific, structural diagnosis
            // (LINKAGE_BROKEN) for that case rather than the less specific CONTENT_MISMATCH,
            // while a pure payload/field tamper (previous_hash untouched) still correctly
            // surfaces as CONTENT_MISMATCH below.
            String expectedPreviousHash = previous == null ? hashChainService.genesisHash() : previous.getRecordHash();
            if (!expectedPreviousHash.equals(record.getPreviousHash())) {
                violations.add(new ViolationDetail(record.getSequenceNo(), record.getId().toString(),
                        ViolationType.LINKAGE_BROKEN, "Stored previous_hash does not match the prior record's hash"));
            }

            String recomputedHash = hashChainService.computeRecordHash(record);
            if (!recomputedHash.equals(record.getRecordHash())) {
                violations.add(new ViolationDetail(record.getSequenceNo(), record.getId().toString(),
                        ViolationType.CONTENT_MISMATCH, "Stored record_hash does not match recomputed hash"));
            }

            long expectedSequenceNo = previous == null ? 1 : previous.getSequenceNo() + 1;
            if (record.getSequenceNo() != expectedSequenceNo) {
                violations.add(new ViolationDetail(record.getSequenceNo(), record.getId().toString(),
                        ViolationType.MISSING_RECORD,
                        "Expected sequence_no " + expectedSequenceNo + " but found " + record.getSequenceNo()
                                + " -- one or more records appear to be missing"));
            }

            for (String problem : redactionCommitmentService.verifyFieldCommitments(record)) {
                violations.add(new ViolationDetail(record.getSequenceNo(), record.getId().toString(),
                        ViolationType.CONTENT_MISMATCH, problem));
            }

            previous = record;
        }

        boolean chainIntact = violations.isEmpty();
        ViolationDetail first = chainIntact ? null : violations.get(0);
        int additional = chainIntact ? 0 : violations.size() - 1;

        return new VerifyResponse(chainIntact, records.size(), first, additional);
    }
}
