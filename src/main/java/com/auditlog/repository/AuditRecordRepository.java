package com.auditlog.repository;

import com.auditlog.entity.AuditRecordEntity;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.OffsetDateTime;
import java.util.Collection;
import java.util.List;
import java.util.UUID;

public interface AuditRecordRepository extends JpaRepository<AuditRecordEntity, UUID> {

    List<AuditRecordEntity> findAllByOrderBySequenceNoAsc();

    java.util.Optional<AuditRecordEntity> findByTenantIdAndIdempotencyKey(String tenantId, String idempotencyKey);

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

    @Query("""
            select a from AuditRecordEntity a
            where a.tenantId = :tenantId
              and (:actorId is null or a.actorId = :actorId)
              and (:resourceId is null or a.resourceId = :resourceId)
            order by a.sequenceNo asc
            """)
    List<AuditRecordEntity> findForExport(@Param("tenantId") String tenantId,
                                           @Param("actorId") String actorId,
                                           @Param("resourceId") String resourceId);

    @Query("select a from AuditRecordEntity a where a.status = com.auditlog.domain.AuditRecordStatus.ACTIVE and a.recordedAt < :cutoff")
    List<AuditRecordEntity> findActiveOlderThan(@Param("cutoff") OffsetDateTime cutoff);

    @Query("""
            select a from AuditRecordEntity a
            where (:tenantId is null or a.tenantId = :tenantId)
              and a.resourceType in :resourceTypes
              and a.eventTimestamp >= :from
              and a.eventTimestamp <= :to
              and (:afterSequenceNo is null or a.sequenceNo > :afterSequenceNo)
            order by a.sequenceNo asc
            """)
    List<AuditRecordEntity> findForComplianceReport(@Param("tenantId") String tenantId,
                                                      @Param("resourceTypes") Collection<String> resourceTypes,
                                                      @Param("from") OffsetDateTime from,
                                                      @Param("to") OffsetDateTime to,
                                                      @Param("afterSequenceNo") Long afterSequenceNo,
                                                      Pageable pageable);
}
