package com.auditlog.controller;

import com.auditlog.dto.ExportBundleResponse;
import com.auditlog.export.ExportBundleService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class ExportController {

    private final ExportBundleService exportBundleService;

    public ExportController(ExportBundleService exportBundleService) {
        this.exportBundleService = exportBundleService;
    }

    @GetMapping("/audit/export")
    public ExportBundleResponse export(@RequestParam(required = false) String tenantId,
                                        @RequestParam(required = false) String actorId,
                                        @RequestParam(required = false) String resourceId) {
        return exportBundleService.export(tenantId, actorId, resourceId);
    }
}
