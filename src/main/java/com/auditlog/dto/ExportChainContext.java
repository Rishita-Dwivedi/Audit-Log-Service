package com.auditlog.dto;

public record ExportChainContext(long firstSequenceNo, long lastSequenceNo, String hashOfLastRecordBeforeRange) {
}
