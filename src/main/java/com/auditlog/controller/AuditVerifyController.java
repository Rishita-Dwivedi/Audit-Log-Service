package com.auditlog.controller;

import com.auditlog.dto.VerifyResponse;
import com.auditlog.service.ChainVerificationService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/audit/verify")
public class AuditVerifyController {

    private final ChainVerificationService chainVerificationService;

    public AuditVerifyController(ChainVerificationService chainVerificationService) {
        this.chainVerificationService = chainVerificationService;
    }

    @GetMapping
    public VerifyResponse verify() {
        return chainVerificationService.verify();
    }
}
