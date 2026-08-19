CREATE TABLE audit_record (
    id UUID NOT NULL PRIMARY KEY,
    sequence_no BIGINT NOT NULL,
    event_type VARCHAR(200) NOT NULL,
    actor_id VARCHAR(200) NOT NULL,
    resource_type VARCHAR(200) NOT NULL,
    resource_id VARCHAR(200) NOT NULL,
    payload CLOB NOT NULL,
    event_timestamp TIMESTAMP WITH TIME ZONE NOT NULL,
    recorded_at TIMESTAMP WITH TIME ZONE NOT NULL,
    record_hash VARCHAR(64) NOT NULL,
    previous_hash VARCHAR(64) NOT NULL
);

ALTER TABLE audit_record ADD CONSTRAINT uq_audit_record_sequence_no UNIQUE (sequence_no);

CREATE INDEX idx_audit_record_actor_id ON audit_record (actor_id);
CREATE INDEX idx_audit_record_resource ON audit_record (resource_type, resource_id);
CREATE INDEX idx_audit_record_event_type ON audit_record (event_type);
CREATE INDEX idx_audit_record_event_timestamp ON audit_record (event_timestamp);

-- Single-row table used as the concurrency-control anchor for sequence/hash assignment.
-- See docs/DECISIONS.md (concurrency-control ADR): writers take a pessimistic lock on this
-- row (SELECT ... FOR UPDATE) so sequence_no and previous_hash assignment cannot race.
CREATE TABLE chain_head (
    id INT NOT NULL PRIMARY KEY,
    last_sequence_no BIGINT NOT NULL,
    last_record_hash VARCHAR(64)
);

INSERT INTO chain_head (id, last_sequence_no, last_record_hash) VALUES (1, 0, NULL);
