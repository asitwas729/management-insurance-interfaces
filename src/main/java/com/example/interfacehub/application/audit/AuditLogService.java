package com.example.interfacehub.application.audit;

import com.example.interfacehub.domain.audit.AuditLog;
import com.example.interfacehub.infrastructure.persistence.AuditLogRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AuditLogService {

    private static final Logger log = LoggerFactory.getLogger(AuditLogService.class);

    private final AuditLogRepository auditLogRepository;

    public AuditLogService(AuditLogRepository auditLogRepository) {
        this.auditLogRepository = auditLogRepository;
    }

    @Transactional
    @Async("applicationTaskExecutor")
    public void record(
        String actor,
        String action,
        String targetType,
        String targetId,
        String beforeValue,
        String afterValue
    ) {
        AuditLog log = AuditLog.record(actor, action, targetType, targetId, beforeValue, afterValue);
        auditLogRepository.save(log);
        this.log.debug("Audit log recorded asynchronously. action={}, targetType={}, targetId={}", action, targetType, targetId);
    }
}
