package com.example.interfacehub.application.scheduler;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "interfacehub.retention")
public class RetentionProperties {

    private int executionHistoryDays = 90;
    private int auditLogDays = 365;
    private int archiveRetentionDays = 730;
    private boolean archiveEnabled = true;

    public int getExecutionHistoryDays() { return executionHistoryDays; }
    public void setExecutionHistoryDays(int executionHistoryDays) { this.executionHistoryDays = executionHistoryDays; }

    public int getAuditLogDays() { return auditLogDays; }
    public void setAuditLogDays(int auditLogDays) { this.auditLogDays = auditLogDays; }

    public int getArchiveRetentionDays() { return archiveRetentionDays; }
    public void setArchiveRetentionDays(int archiveRetentionDays) { this.archiveRetentionDays = archiveRetentionDays; }

    public boolean isArchiveEnabled() { return archiveEnabled; }
    public void setArchiveEnabled(boolean archiveEnabled) { this.archiveEnabled = archiveEnabled; }
}
