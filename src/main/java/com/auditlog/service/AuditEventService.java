package com.auditlog.service;

import com.auditlog.dto.AuditEventCreateRequest;
import com.auditlog.dto.AuditEventResponse;
import com.auditlog.entity.AuditRecordEntity;
import com.auditlog.entity.ChainHeadEntity;
import com.auditlog.hash.HashChainService;
import com.auditlog.repository.AuditRecordRepository;
import com.auditlog.repository.ChainHeadRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.UUID;

@Service
public class AuditEventService {

    private final AuditRecordRepository auditRecordRepository;
    private final ChainHeadRepository chainHeadRepository;
    private final HashChainService hashChainService;

    public AuditEventService(AuditRecordRepository auditRecordRepository,
                              ChainHeadRepository chainHeadRepository,
                              HashChainService hashChainService) {
        this.auditRecordRepository = auditRecordRepository;
        this.chainHeadRepository = chainHeadRepository;
        this.hashChainService = hashChainService;
    }

    /**
     * Concurrency control: lockHead() takes a pessimistic row lock (SELECT ... FOR UPDATE) on
     * the single chain_head row for the duration of this transaction, so two concurrent
     * writers cannot both read the same "current head" and race to claim the same
     * sequence_no / previous_hash. The second writer blocks until the first commits (advancing
     * chain_head), then proceeds from the now-current state. This intentionally serializes
     * writes -- see docs/DECISIONS.md for the alternatives considered (optimistic retry,
     * in-memory lock) and why this was chosen.
     */
    @Transactional
    public AuditEventResponse append(AuditEventCreateRequest request) {
        ChainHeadEntity head = chainHeadRepository.lockHead()
                .orElseThrow(() -> new IllegalStateException("chain_head row is missing; schema not initialized correctly"));

        long previousSequenceNo = head.getLastSequenceNo();
        String previousHash = previousSequenceNo == 0 ? hashChainService.genesisHash() : head.getLastRecordHash();
        long nextSequenceNo = previousSequenceNo + 1;

        // Truncated to a fixed precision floor *before* hashing, deliberately: the hash must be
        // computed from the value exactly as it will be persisted and re-read, not from
        // whatever sub-millisecond precision the caller happened to supply. Without this, a
        // caller-supplied Instant.now()-style timestamp (nanosecond precision) can silently
        // mismatch the value the database actually stores/returns, making ChainVerificationService
        // recompute a different hash than was stored -- a false CONTENT_MISMATCH with no real
        // tampering involved. Caught via ConcurrentAppendTest, where OffsetDateTime.now() timestamps
        // exposed this; fixed-timestamp tests never hit it since their timestamps already had zero
        // sub-second precision.
        OffsetDateTime eventTimestamp = request.timestamp().truncatedTo(java.time.temporal.ChronoUnit.MILLIS);
        OffsetDateTime recordedAt = OffsetDateTime.now().truncatedTo(java.time.temporal.ChronoUnit.MILLIS);
        String recordHash = hashChainService.computeRecordHash(
                request.eventType(), request.actorId(), request.resourceType(), request.resourceId(),
                request.payload(), eventTimestamp, nextSequenceNo, previousHash);

        AuditRecordEntity entity = new AuditRecordEntity(
                UUID.randomUUID(), nextSequenceNo, request.eventType(), request.actorId(),
                request.resourceType(), request.resourceId(), request.payload(),
                eventTimestamp, recordedAt, recordHash, previousHash);

        auditRecordRepository.save(entity);
        head.advance(nextSequenceNo, recordHash);

        return AuditRecordMapper.toResponse(entity);
    }
}
