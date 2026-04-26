package com.example.interfacehub.domain.audit;

public final class AuditAction {
    private AuditAction() {}

    // Security Actions
    public static final String AUTHENTICATION_FAILED = "AUTHENTICATION_FAILED";
    public static final String TOKEN_REVOKED = "TOKEN_REVOKED";
    public static final String LOGIN_SUCCESS = "LOGIN_SUCCESS";

    // Resource Actions
    public static final String CREATE = "CREATE";
    public static final String UPDATE = "UPDATE";
    public static final String DELETE = "DELETE";
    public static final String PUBLISH_CONFIG = "PUBLISH_CONFIG";
    public static final String CHANGE_STATUS = "CHANGE_STATUS";
    
    // Execution & Retry Actions
    public static final String MANUAL_EXECUTION = "MANUAL_EXECUTION";
    public static final String REPROCESSED = "REPROCESSED";
    public static final String REPLAY_DLQ = "REPLAY_DLQ";
    public static final String APPROVE = "APPROVE";
    public static final String REJECT = "REJECT";

    // System Actions
    public static final String DATA_RETENTION_EXECUTED = "DATA_RETENTION_EXECUTED";
    public static final String CONFIG_EXPORTED = "CONFIG_EXPORTED";
    public static final String CONFIG_IMPORTED = "CONFIG_IMPORTED";
}
