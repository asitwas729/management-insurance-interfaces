package com.example.interfacehub.application.audit;

import com.example.interfacehub.domain.audit.AuditLog;
import com.example.interfacehub.infrastructure.persistence.AuditLogRepository;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AuditLogQueryService {

    private final AuditLogRepository auditLogRepository;

    public AuditLogQueryService(AuditLogRepository auditLogRepository) {
        this.auditLogRepository = auditLogRepository;
    }

    @Transactional(readOnly = true)
    public Page<AuditLog> search(
        String actor,
        String action,
        String targetType,
        String targetId,
        LocalDate fromDate,
        LocalDate toDate,
        Pageable pageable
    ) {
        LocalDateTime from = fromDate == null ? null : fromDate.atStartOfDay();
        LocalDateTime to = toDate == null ? null : toDate.atTime(LocalTime.MAX);
        return auditLogRepository.search(actor, action, targetType, targetId, from, to, pageable);
    }
}
