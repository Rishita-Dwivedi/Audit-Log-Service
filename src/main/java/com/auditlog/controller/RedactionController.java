package com.auditlog.controller;

import com.auditlog.dto.AuditEventResponse;
import com.auditlog.dto.RedactRequest;
import com.auditlog.service.RedactionService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

/**
 * A single, narrowly-scoped POST mapping -- not a general update/delete. Append-only still
 * holds: no PUT/PATCH/DELETE mapping exists anywhere (com.auditlog.controller.AppendOnlyApiTest
 * enforces this across all controllers, including this one).
 */
@RestController
@RequestMapping("/audit/events")
public class RedactionController {

    private final RedactionService redactionService;

    public RedactionController(RedactionService redactionService) {
        this.redactionService = redactionService;
    }

    @PostMapping("/{id}/redact")
    public AuditEventResponse redact(@PathVariable UUID id, @Valid @RequestBody RedactRequest request) {
        return redactionService.redact(id, request.fields(), request.reason());
    }
}
