package com.example.interfacehub.application.audit;

import com.example.interfacehub.domain.audit.AuditLog;
import com.example.interfacehub.infrastructure.persistence.AuditLogRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AuditLogService {

    private final AuditLogRepository auditLogRepository;

    public AuditLogService(AuditLogRepository auditLogRepository) {
        this.auditLogRepository = auditLogRepository;
    }

    @Transactional
    public AuditLog record(
        String actor,
        String action,
        String targetType,
        String targetId,
        String beforeValue,
        String afterValue
    ) {
        AuditLog log = AuditLog.record(actor, action, targetType, targetId, beforeValue, afterValue);
        return auditLogRepository.save(log);
    }
}
