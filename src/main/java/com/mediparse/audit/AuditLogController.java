package com.mediparse.audit;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/audit-logs")
@PreAuthorize("hasRole('ADMIN')")
public class AuditLogController {

    private final AuditLogRepository auditLogRepository;

    public AuditLogController(AuditLogRepository auditLogRepository) {
        this.auditLogRepository = auditLogRepository;
    }

    @GetMapping
    public Page<AuditLogResponse> list(@RequestParam(required = false) UUID actorUserId, Pageable pageable) {
        Page<AuditLog> page = actorUserId != null
                ? auditLogRepository.findByActorUserId(actorUserId, pageable)
                : auditLogRepository.findAll(pageable);
        return page.map(AuditLogResponse::from);
    }

    @GetMapping("/resource")
    public Page<AuditLogResponse> forResource(@RequestParam String resourceType,
                                               @RequestParam String resourceId,
                                               Pageable pageable) {
        return auditLogRepository.findByResourceTypeAndResourceId(resourceType, resourceId, pageable)
                .map(AuditLogResponse::from);
    }
}
