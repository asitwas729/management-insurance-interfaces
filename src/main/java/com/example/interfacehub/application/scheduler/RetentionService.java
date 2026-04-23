package com.example.interfacehub.application.scheduler;

import com.example.interfacehub.application.audit.AuditLogService;
import com.example.interfacehub.domain.audit.AuditAction;
import java.time.LocalDateTime;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class RetentionService {

    private static final Logger log = LoggerFactory.getLogger(RetentionService.class);

    private final JdbcTemplate jdbcTemplate;
    private final RetentionProperties retentionProperties;
    private final AuditLogService auditLogService;

    public RetentionService(
        JdbcTemplate jdbcTemplate,
        RetentionProperties retentionProperties,
        AuditLogService auditLogService
    ) {
        this.jdbcTemplate = jdbcTemplate;
        this.retentionProperties = retentionProperties;
        this.auditLogService = auditLogService;
    }

    @Transactional
    public RetentionResult archiveAndPurge() {
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime execCutoff = now.minusDays(retentionProperties.getExecutionHistoryDays());
        LocalDateTime auditCutoff = now.minusDays(retentionProperties.getAuditLogDays());
        LocalDateTime archiveCutoff = now.minusDays(retentionProperties.getArchiveRetentionDays());

        int execArchived = 0;
        int execDeleted = 0;
        int auditArchived = 0;
        int auditDeleted = 0;
        int archiveExecDeleted = 0;
        int archiveAuditDeleted = 0;

        if (retentionProperties.isArchiveEnabled()) {
            execArchived = jdbcTemplate.update("""
                INSERT INTO archive_execution_history
                    (id, execution_id, interface_code, protocol_type, trigger_type, status,
                     started_at, ended_at, latency_millis, request_payload, response_payload,
                     error_code, error_message)
                SELECT id, execution_id, interface_code, protocol_type, trigger_type, status,
                       started_at, ended_at, latency_millis, request_payload, response_payload,
                       error_code, error_message
                FROM execution_history
                WHERE started_at < ?
                  AND id NOT IN (SELECT id FROM archive_execution_history)
                """, execCutoff);

            auditArchived = jdbcTemplate.update("""
                INSERT INTO archive_audit_log
                    (id, actor, action, target_type, target_id, before_state, after_state, created_at)
                SELECT id, actor, action, target_type, target_id, before_value, after_value, created_at
                FROM audit_log
                WHERE created_at < ?
                  AND id NOT IN (SELECT id FROM archive_audit_log)
                """, auditCutoff);
        }

        execDeleted = jdbcTemplate.update(
            "DELETE FROM execution_history WHERE started_at < ?", execCutoff);

        auditDeleted = jdbcTemplate.update(
            "DELETE FROM audit_log WHERE created_at < ?", auditCutoff);

        // Purge archive tables
        archiveExecDeleted = jdbcTemplate.update(
            "DELETE FROM archive_execution_history WHERE started_at < ?", archiveCutoff);

        archiveAuditDeleted = jdbcTemplate.update(
            "DELETE FROM archive_audit_log WHERE created_at < ?", archiveCutoff);

        // Security token cleanup: delete expired blacklist tokens and expired/revoked refresh tokens
        int expiredBlacklistDeleted = jdbcTemplate.update(
            "DELETE FROM token_blacklist WHERE expires_at < ?", now);
        
        int expiredRefreshTokenDeleted = jdbcTemplate.update(
            "DELETE FROM refresh_token WHERE expires_at < ? OR revoked = true", now);

        log.info("[Retention] execution_history: archived={}, deleted={}; audit_log: archived={}, deleted={}; archive_purge: exec={}, audit={}; tokens: blacklist_deleted={}, refresh_deleted={}",
            execArchived, execDeleted, auditArchived, auditDeleted, archiveExecDeleted, archiveAuditDeleted, expiredBlacklistDeleted, expiredRefreshTokenDeleted);

        String summary = String.format(
            "ExecArchived: %d, ExecDeleted: %d, AuditArchived: %d, AuditDeleted: %d, ArchiveExecDeleted: %d, ArchiveAuditDeleted: %d, BlacklistDeleted: %d, RefreshDeleted: %d",
            execArchived, execDeleted, auditArchived, auditDeleted, archiveExecDeleted, archiveAuditDeleted, expiredBlacklistDeleted, expiredRefreshTokenDeleted
        );

        auditLogService.record(
            "SYSTEM",
            AuditAction.DATA_RETENTION_EXECUTED,
            "SYSTEM_MAINTENANCE",
            "RETENTION",
            null,
            summary
        );

        return new RetentionResult(execArchived, execDeleted, auditArchived, auditDeleted, now);
    }

    public record RetentionResult(
        int execArchived,
        int execDeleted,
        int auditArchived,
        int auditDeleted,
        LocalDateTime ranAt
    ) {}
}
