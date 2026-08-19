package com.auditlog.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * Single-row table (id=1, seeded by V1__init_schema.sql) used as the pessimistic-lock
 * anchor for write-path concurrency control. See docs/DECISIONS.md, concurrency-control ADR.
 */
@Entity
@Table(name = "chain_head")
public class ChainHeadEntity {

    @Id
    private Integer id;

    @Column(name = "last_sequence_no", nullable = false)
    private long lastSequenceNo;

    @Column(name = "last_record_hash", length = 64)
    private String lastRecordHash;

    protected ChainHeadEntity() {
        // JPA
    }

    public Integer getId() {
        return id;
    }

    public long getLastSequenceNo() {
        return lastSequenceNo;
    }

    public String getLastRecordHash() {
        return lastRecordHash;
    }

    public void advance(long newSequenceNo, String newRecordHash) {
        this.lastSequenceNo = newSequenceNo;
        this.lastRecordHash = newRecordHash;
    }
}
