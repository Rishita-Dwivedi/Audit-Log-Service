package com.auditlog.controller;

import com.auditlog.dto.RetentionApplyResponse;
import com.auditlog.service.RetentionService;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class RetentionController {

    private final RetentionService retentionService;

    public RetentionController(RetentionService retentionService) {
        this.retentionService = retentionService;
    }

    @PostMapping("/audit/retention/apply")
    public RetentionApplyResponse apply(@RequestParam(required = false) Integer windowDays) {
        return retentionService.applyRetention(windowDays);
    }
}
