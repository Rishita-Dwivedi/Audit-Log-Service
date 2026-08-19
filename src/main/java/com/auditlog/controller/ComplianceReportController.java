package com.auditlog.controller;

import com.auditlog.dto.AuditEventPageResponse;
import com.auditlog.service.ComplianceReportService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.OffsetDateTime;

@RestController
public class ComplianceReportController {

    private final ComplianceReportService complianceReportService;

    public ComplianceReportController(ComplianceReportService complianceReportService) {
        this.complianceReportService = complianceReportService;
    }

    @GetMapping("/audit/compliance-report")
    public AuditEventPageResponse report(@RequestParam(required = false) String tenantId,
                                          @RequestParam(required = false) String from,
                                          @RequestParam(required = false) String to,
                                          @RequestParam(required = false) Long afterSequenceNo,
                                          @RequestParam(required = false) Integer pageSize) {
        OffsetDateTime fromTs = from != null ? OffsetDateTime.parse(from) : null;
        OffsetDateTime toTs = to != null ? OffsetDateTime.parse(to) : null;
        return complianceReportService.generateReport(tenantId, fromTs, toTs, afterSequenceNo, pageSize);
    }
}
