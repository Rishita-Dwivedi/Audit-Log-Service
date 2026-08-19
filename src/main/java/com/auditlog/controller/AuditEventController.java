package com.auditlog.controller;

import com.auditlog.dto.AuditEventCreateRequest;
import com.auditlog.dto.AuditEventResponse;
import com.auditlog.service.AuditEventService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Deliberately append-only: only a POST (create) mapping exists here. No PUT/PATCH/DELETE
 * mapping exists anywhere in this controller -- see
 * com.auditlog.controller.AppendOnlyApiTest, which fails the build if one is ever added.
 * Query (GET) support is added in Milestone 5.
 */
@RestController
@RequestMapping("/audit/events")
public class AuditEventController {

    private final AuditEventService auditEventService;

    public AuditEventController(AuditEventService auditEventService) {
        this.auditEventService = auditEventService;
    }

    @PostMapping
    public ResponseEntity<AuditEventResponse> create(@Valid @RequestBody AuditEventCreateRequest request) {
        AuditEventResponse response = auditEventService.append(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }
}
