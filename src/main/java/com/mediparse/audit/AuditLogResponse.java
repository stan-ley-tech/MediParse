package com.mediparse.audit;

import java.time.Instant;
import java.util.UUID;

public record AuditLogResponse(
        UUID id,
        UUID actorUserId,
        AuditAction action,
        String resourceType,
        String resourceId,
        String detail,
        String ipAddress,
        Instant createdAt
) {
    public static AuditLogResponse from(AuditLog log) {
        return new AuditLogResponse(log.getId(), log.getActorUserId(), log.getAction(),
                log.getResourceType(), log.getResourceId(), log.getDetail(), log.getIpAddress(), log.getCreatedAt());
    }
}
