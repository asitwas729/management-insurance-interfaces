package com.example.interfacehub.application.scheduler;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Min;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Validated
@ConfigurationProperties(prefix = "interfacehub.retention")
public class RetentionProperties {

    @Min(value = 1, message = "execution-history-days must be >= 1")
    private int executionHistoryDays = 90;
    @Min(value = 1, message = "audit-log-days must be >= 1")
    private int auditLogDays = 365;
    @Min(value = 1, message = "archive-retention-days must be >= 1")
    private int archiveRetentionDays = 730;
    private boolean archiveEnabled = true;
    @Min(value = 1, message = "revoked-refresh-token-retention-days must be >= 1")
    private int revokedRefreshTokenRetentionDays = 7;
    @Min(value = 1, message = "purge-chunk-size must be >= 1")
    private int purgeChunkSize = 1000;

    public int getExecutionHistoryDays() { return executionHistoryDays; }
    public void setExecutionHistoryDays(int executionHistoryDays) { this.executionHistoryDays = executionHistoryDays; }

    public int getAuditLogDays() { return auditLogDays; }
    public void setAuditLogDays(int auditLogDays) { this.auditLogDays = auditLogDays; }

    public int getArchiveRetentionDays() { return archiveRetentionDays; }
    public void setArchiveRetentionDays(int archiveRetentionDays) { this.archiveRetentionDays = archiveRetentionDays; }

    public boolean isArchiveEnabled() { return archiveEnabled; }
    public void setArchiveEnabled(boolean archiveEnabled) { this.archiveEnabled = archiveEnabled; }

    public int getRevokedRefreshTokenRetentionDays() { return revokedRefreshTokenRetentionDays; }
    public void setRevokedRefreshTokenRetentionDays(int revokedRefreshTokenRetentionDays) {
        this.revokedRefreshTokenRetentionDays = revokedRefreshTokenRetentionDays;
    }

    public int getPurgeChunkSize() { return purgeChunkSize; }
    public void setPurgeChunkSize(int purgeChunkSize) { this.purgeChunkSize = purgeChunkSize; }

    @AssertTrue(message = "archive-retention-days must be >= execution-history-days when archive is enabled")
    public boolean isExecutionArchiveWindowValid() {
        return !archiveEnabled || archiveRetentionDays >= executionHistoryDays;
    }

    @AssertTrue(message = "archive-retention-days must be >= audit-log-days when archive is enabled")
    public boolean isAuditArchiveWindowValid() {
        return !archiveEnabled || archiveRetentionDays >= auditLogDays;
    }
}
