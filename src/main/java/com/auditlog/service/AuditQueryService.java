package com.auditlog.service;

import com.auditlog.dto.AuditEventPageResponse;
import com.auditlog.dto.AuditEventResponse;
import com.auditlog.entity.AuditRecordEntity;
import com.auditlog.exception.ResourceNotFoundException;
import com.auditlog.repository.AuditRecordRepository;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
public class AuditQueryService {

    private static final int DEFAULT_PAGE_SIZE = 50;
    private static final int MAX_PAGE_SIZE = 200;

    private final AuditRecordRepository auditRecordRepository;

    public AuditQueryService(AuditRecordRepository auditRecordRepository) {
        this.auditRecordRepository = auditRecordRepository;
    }

    public AuditEventPageResponse search(String actorId, String resourceType, String resourceId, String eventType,
                                          OffsetDateTime from, OffsetDateTime to, Long afterSequenceNo, Integer pageSize) {
        int effectivePageSize = clampPageSize(pageSize);

        // Fetch one extra row to detect whether more results exist, avoiding a separate count query.
        List<AuditRecordEntity> rows = auditRecordRepository.search(actorId, resourceType, resourceId, eventType,
                from, to, afterSequenceNo, PageRequest.of(0, effectivePageSize + 1));

        boolean hasMore = rows.size() > effectivePageSize;
        List<AuditRecordEntity> page = hasMore ? rows.subList(0, effectivePageSize) : rows;

        List<AuditEventResponse> items = page.stream().map(AuditRecordMapper::toResponse).collect(Collectors.toList());
        Long nextCursor = page.isEmpty() ? afterSequenceNo : page.get(page.size() - 1).getSequenceNo();

        return new AuditEventPageResponse(items, effectivePageSize, nextCursor, hasMore);
    }

    public AuditEventResponse findById(UUID id) {
        return auditRecordRepository.findById(id)
                .map(AuditRecordMapper::toResponse)
                .orElseThrow(() -> new ResourceNotFoundException("Audit record not found: " + id));
    }

    private int clampPageSize(Integer requested) {
        if (requested == null) {
            return DEFAULT_PAGE_SIZE;
        }
        return Math.max(1, Math.min(requested, MAX_PAGE_SIZE));
    }
}
