package com.example.interfacehub.application.scheduler;

import com.example.interfacehub.application.audit.AuditLogService;
import com.example.interfacehub.domain.audit.AuditAction;
import java.time.LocalDateTime;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

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

    public RetentionResult archiveAndPurge() {
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime execCutoff = now.minusDays(retentionProperties.getExecutionHistoryDays());
        LocalDateTime auditCutoff = now.minusDays(retentionProperties.getAuditLogDays());
        LocalDateTime archiveCutoff = now.minusDays(retentionProperties.getArchiveRetentionDays());
        LocalDateTime revokedCutoff = now.minusDays(retentionProperties.getRevokedRefreshTokenRetentionDays());
        int chunkSize = Math.max(1, retentionProperties.getPurgeChunkSize());

        int execArchived = 0;
        int execDeleted = 0;
        int auditArchived = 0;
        int auditDeleted = 0;
        int archiveExecDeleted = 0;
        int archiveAuditDeleted = 0;
        int refreshArchived = 0;

        if (retentionProperties.isArchiveEnabled()) {
            execArchived = archiveExecutionHistory(execCutoff, chunkSize);
            auditArchived = archiveAuditLog(auditCutoff, chunkSize);
            refreshArchived = archiveRefreshTokens(now, revokedCutoff, chunkSize);
        }

        execDeleted = deleteExecutionHistory(execCutoff, chunkSize);
        auditDeleted = deleteAuditLog(auditCutoff, chunkSize);

        // Purge archive tables
        archiveExecDeleted = deleteArchiveExecutionHistory(archiveCutoff, chunkSize);
        archiveAuditDeleted = deleteArchiveAuditLog(archiveCutoff, chunkSize);

        // Security token cleanup (chunked): delete expired blacklist tokens and old expired/revoked refresh tokens
        int expiredBlacklistDeleted = deleteExpiredTokenBlacklist(now, chunkSize);
        
        int expiredRefreshTokenDeleted = deleteRefreshTokens(now, revokedCutoff, chunkSize);

        log.info("[Retention] execution_history: archived={}, deleted={}; audit_log: archived={}, deleted={}; archive_purge: exec={}, audit={}; tokens: refresh_archived={}, blacklist_deleted={}, refresh_deleted={}",
            execArchived, execDeleted, auditArchived, auditDeleted, archiveExecDeleted, archiveAuditDeleted, refreshArchived, expiredBlacklistDeleted, expiredRefreshTokenDeleted);

        String summary = String.format(
            "ExecArchived: %d, ExecDeleted: %d, AuditArchived: %d, AuditDeleted: %d, ArchiveExecDeleted: %d, ArchiveAuditDeleted: %d, RefreshArchived: %d, BlacklistDeleted: %d, RefreshDeleted: %d",
            execArchived, execDeleted, auditArchived, auditDeleted, archiveExecDeleted, archiveAuditDeleted, refreshArchived, expiredBlacklistDeleted, expiredRefreshTokenDeleted
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

    private int archiveExecutionHistory(LocalDateTime execCutoff, int chunkSize) {
        return runChunked(() -> jdbcTemplate.update("""
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
            LIMIT ?
            """, execCutoff, chunkSize), chunkSize);
    }

    private int archiveAuditLog(LocalDateTime auditCutoff, int chunkSize) {
        return runChunked(() -> jdbcTemplate.update("""
            INSERT INTO archive_audit_log
                (id, actor, action, target_type, target_id, before_state, after_state, created_at)
            SELECT id, actor, action, target_type, target_id, before_value, after_value, created_at
            FROM audit_log
            WHERE created_at < ?
              AND id NOT IN (SELECT id FROM archive_audit_log)
            LIMIT ?
            """, auditCutoff, chunkSize), chunkSize);
    }

    private int archiveRefreshTokens(LocalDateTime now, LocalDateTime revokedCutoff, int chunkSize) {
        return runChunked(() -> jdbcTemplate.update("""
            INSERT INTO archive_refresh_token
                (id, username, token, expires_at, revoked, revoked_at, created_at)
            SELECT id, username, token, expires_at, revoked, revoked_at, created_at
            FROM refresh_token
            WHERE (expires_at < ? OR (revoked = true AND COALESCE(revoked_at, created_at) < ?))
              AND id NOT IN (SELECT id FROM archive_refresh_token)
            LIMIT ?
            """, now, revokedCutoff, chunkSize), chunkSize);
    }

    private int deleteExecutionHistory(LocalDateTime execCutoff, int chunkSize) {
        return runChunked(() -> jdbcTemplate.update("""
            DELETE FROM execution_history
            WHERE id IN (
                SELECT id FROM execution_history
                WHERE started_at < ?
                LIMIT ?
            )
            """, execCutoff, chunkSize), chunkSize);
    }

    private int deleteAuditLog(LocalDateTime auditCutoff, int chunkSize) {
        return runChunked(() -> jdbcTemplate.update("""
            DELETE FROM audit_log
            WHERE id IN (
                SELECT id FROM audit_log
                WHERE created_at < ?
                LIMIT ?
            )
            """, auditCutoff, chunkSize), chunkSize);
    }

    private int deleteArchiveExecutionHistory(LocalDateTime archiveCutoff, int chunkSize) {
        return runChunked(() -> jdbcTemplate.update("""
            DELETE FROM archive_execution_history
            WHERE id IN (
                SELECT id FROM archive_execution_history
                WHERE started_at < ?
                LIMIT ?
            )
            """, archiveCutoff, chunkSize), chunkSize);
    }

    private int deleteArchiveAuditLog(LocalDateTime archiveCutoff, int chunkSize) {
        return runChunked(() -> jdbcTemplate.update("""
            DELETE FROM archive_audit_log
            WHERE id IN (
                SELECT id FROM archive_audit_log
                WHERE created_at < ?
                LIMIT ?
            )
            """, archiveCutoff, chunkSize), chunkSize);
    }

    private int deleteExpiredTokenBlacklist(LocalDateTime now, int chunkSize) {
        return runChunked(() -> jdbcTemplate.update("""
            DELETE FROM token_blacklist
            WHERE id IN (
                SELECT id FROM token_blacklist
                WHERE expires_at < ?
                LIMIT ?
            )
            """, now, chunkSize), chunkSize);
    }

    private int deleteRefreshTokens(LocalDateTime now, LocalDateTime revokedCutoff, int chunkSize) {
        return runChunked(() -> jdbcTemplate.update("""
            DELETE FROM refresh_token
            WHERE id IN (
                SELECT id FROM refresh_token
                WHERE expires_at < ?
                   OR (revoked = true AND COALESCE(revoked_at, created_at) < ?)
                LIMIT ?
            )
            """, now, revokedCutoff, chunkSize), chunkSize);
    }

    private int runChunked(ChunkedOperation operation, int chunkSize) {
        int total = 0;
        int changed;
        do {
            changed = operation.execute();
            total += changed;
        } while (changed == chunkSize);
        return total;
    }

    @FunctionalInterface
    private interface ChunkedOperation {
        int execute();
    }

    public record RetentionResult(
        int execArchived,
        int execDeleted,
        int auditArchived,
        int auditDeleted,
        LocalDateTime ranAt
    ) {}
}
