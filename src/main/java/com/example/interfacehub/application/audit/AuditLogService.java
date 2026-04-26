package com.example.interfacehub.application.audit;

import com.example.interfacehub.domain.audit.AuditLog;
import com.example.interfacehub.infrastructure.persistence.AuditLogRepository;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Tag;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class AuditLogService {

    private static final Logger log = LoggerFactory.getLogger(AuditLogService.class);

    private final AuditLogRepository auditLogRepository;
    private final MeterRegistry meterRegistry;

    public AuditLogService(AuditLogRepository auditLogRepository, MeterRegistry meterRegistry) {
        this.auditLogRepository = auditLogRepository;
        this.meterRegistry = meterRegistry;
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
        AuditLog auditLog = AuditLog.record(actor, action, targetType, targetId, beforeValue, afterValue);
        auditLogRepository.save(auditLog);

        // Record metric for monitoring
        meterRegistry.counter("admin.action",
            List.of(
                Tag.of("actor", actor != null ? actor : "system"),
                Tag.of("action", action),
                Tag.of("target_type", targetType)
            )
        ).increment();

        log.debug("Audit log recorded asynchronously and metric incremented. action={}, targetType={}, targetId={}", action, targetType, targetId);
    }
}
