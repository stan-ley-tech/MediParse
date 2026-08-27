package com.mediparse.audit;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.util.Optional;
import java.util.UUID;

/**
 * Writes audit trail entries in their own transaction so a record survives
 * even when the business operation it describes (e.g. a failed processing
 * attempt) rolls back.
 */
@Service
public class AuditLogService {

    private final AuditLogRepository auditLogRepository;

    public AuditLogService(AuditLogRepository auditLogRepository) {
        this.auditLogRepository = auditLogRepository;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void record(UUID actorUserId, AuditAction action, String resourceType, String resourceId, String detail) {
        String ipAddress = currentRequestIp().orElse(null);
        auditLogRepository.save(new AuditLog(actorUserId, action, resourceType, resourceId, detail, ipAddress));
    }

    public void recordSystem(AuditAction action, String resourceType, String resourceId, String detail) {
        record(null, action, resourceType, resourceId, detail);
    }

    private Optional<String> currentRequestIp() {
        if (!(RequestContextHolder.getRequestAttributes() instanceof ServletRequestAttributes attrs)) {
            return Optional.empty();
        }
        HttpServletRequest request = attrs.getRequest();
        String forwardedFor = request.getHeader("X-Forwarded-For");
        if (forwardedFor != null && !forwardedFor.isBlank()) {
            return Optional.of(forwardedFor.split(",")[0].trim());
        }
        return Optional.ofNullable(request.getRemoteAddr());
    }
}
