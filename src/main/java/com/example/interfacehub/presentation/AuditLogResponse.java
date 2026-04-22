package com.example.interfacehub.presentation;

import com.example.interfacehub.domain.audit.AuditLog;
import java.time.LocalDateTime;

public record AuditLogResponse(
    Long id,
    String actor,
    String action,
    String targetType,
    String targetId,
    String beforeValue,
    String afterValue,
    LocalDateTime createdAt
) {
    public static AuditLogResponse from(AuditLog auditLog) {
        return new AuditLogResponse(
            auditLog.getId(),
            auditLog.getActor(),
            auditLog.getAction(),
            auditLog.getTargetType(),
            auditLog.getTargetId(),
            auditLog.getBeforeValue(),
            auditLog.getAfterValue(),
            auditLog.getCreatedAt()
        );
    }
}
