package com.auditlog.repository;

import com.auditlog.entity.ChainHeadEntity;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;

import java.util.Optional;

public interface ChainHeadRepository extends JpaRepository<ChainHeadEntity, Integer> {

    /**
     * Pessimistic row lock (SELECT ... FOR UPDATE) on the single chain_head row. This is the
     * concurrency-control mechanism for sequence/hash assignment: see docs/DECISIONS.md.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select c from ChainHeadEntity c where c.id = 1")
    Optional<ChainHeadEntity> lockHead();
}
