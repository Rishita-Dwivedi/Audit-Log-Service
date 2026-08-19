package com.auditlog.controller;

import com.auditlog.dto.AuditEventCreateRequest;
import com.auditlog.dto.AuditEventPageResponse;
import com.auditlog.dto.AuditEventResponse;
import com.auditlog.service.AuditEventService;
import com.auditlog.service.AuditQueryService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * Deliberately append-only: only POST (create) and GET (read) mappings exist here. No
 * PUT/PATCH/DELETE mapping exists anywhere in this controller -- see
 * com.auditlog.controller.AppendOnlyApiTest, which fails the build if one is ever added.
 */
@RestController
@RequestMapping("/audit/events")
public class AuditEventController {

    private final AuditEventService auditEventService;
    private final AuditQueryService auditQueryService;

    public AuditEventController(AuditEventService auditEventService, AuditQueryService auditQueryService) {
        this.auditEventService = auditEventService;
        this.auditQueryService = auditQueryService;
    }

    @PostMapping
    public ResponseEntity<AuditEventResponse> create(@Valid @RequestBody AuditEventCreateRequest request) {
        AuditEventResponse response = auditEventService.append(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @GetMapping
    public ResponseEntity<AuditEventPageResponse> search(
            @RequestParam(required = false) String tenantId,
            @RequestParam(required = false) String actorId,
            @RequestParam(required = false) String resourceType,
            @RequestParam(required = false) String resourceId,
            @RequestParam(required = false) String eventType,
            @RequestParam(required = false) String from,
            @RequestParam(required = false) String to,
            @RequestParam(required = false) Long afterSequenceNo,
            @RequestParam(required = false) Integer pageSize) {
        OffsetDateTime fromTs = from != null ? OffsetDateTime.parse(from) : null;
        OffsetDateTime toTs = to != null ? OffsetDateTime.parse(to) : null;
        return ResponseEntity.ok(auditQueryService.search(tenantId, actorId, resourceType, resourceId, eventType,
                fromTs, toTs, afterSequenceNo, pageSize));
    }

    @GetMapping("/{id}")
    public ResponseEntity<AuditEventResponse> findById(@PathVariable UUID id) {
        return ResponseEntity.ok(auditQueryService.findById(id));
    }
}
