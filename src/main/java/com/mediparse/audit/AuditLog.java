package com.mediparse.audit;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "audit_log")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class AuditLog {

    @Id
    @GeneratedValue
    private UUID id;

    @Column(name = "actor_user_id")
    private UUID actorUserId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private AuditAction action;

    @Column(name = "resource_type", nullable = false)
    private String resourceType;

    @Column(name = "resource_id")
    private String resourceId;

    private String detail;

    @Column(name = "ip_address")
    private String ipAddress;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    public AuditLog(UUID actorUserId, AuditAction action, String resourceType, String resourceId,
                     String detail, String ipAddress) {
        this.actorUserId = actorUserId;
        this.action = action;
        this.resourceType = resourceType;
        this.resourceId = resourceId;
        this.detail = detail;
        this.ipAddress = ipAddress;
    }

    @PrePersist
    void onCreate() {
        createdAt = Instant.now();
    }
}
