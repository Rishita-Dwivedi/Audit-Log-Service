package com.auditlog.repository;

import com.auditlog.entity.AuditRecordEntity;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

public interface AuditRecordRepository extends JpaRepository<AuditRecordEntity, UUID> {

    List<AuditRecordEntity> findAllByOrderBySequenceNoAsc();

    @Query("""
            select a from AuditRecordEntity a
            where (:tenantId is null or a.tenantId = :tenantId)
              and (:actorId is null or a.actorId = :actorId)
              and (:resourceType is null or a.resourceType = :resourceType)
              and (:resourceId is null or a.resourceId = :resourceId)
              and (:eventType is null or a.eventType = :eventType)
              and (:from is null or a.eventTimestamp >= :from)
              and (:to is null or a.eventTimestamp <= :to)
              and (:afterSequenceNo is null or a.sequenceNo > :afterSequenceNo)
            order by a.sequenceNo asc
            """)
    List<AuditRecordEntity> search(@Param("tenantId") String tenantId,
                                    @Param("actorId") String actorId,
                                    @Param("resourceType") String resourceType,
                                    @Param("resourceId") String resourceId,
                                    @Param("eventType") String eventType,
                                    @Param("from") OffsetDateTime from,
                                    @Param("to") OffsetDateTime to,
                                    @Param("afterSequenceNo") Long afterSequenceNo,
                                    Pageable pageable);
}
