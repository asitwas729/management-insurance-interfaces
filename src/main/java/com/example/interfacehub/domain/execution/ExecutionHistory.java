package com.example.interfacehub.domain.execution;

import com.example.interfacehub.domain.interfaceconfig.ProtocolType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import java.time.LocalDateTime;

@Entity
public class ExecutionHistory {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 100)
    private String executionId;

    @Column(nullable = false, length = 100)
    private String interfaceCode;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private ProtocolType protocolType;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private TriggerType triggerType;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private ExecutionStatus status;

    @Column(nullable = false)
    private LocalDateTime startedAt;

    private LocalDateTime endedAt;

    private Long latencyMillis;

    @Column(columnDefinition = "TEXT")
    private String requestPayload;

    @Column(columnDefinition = "TEXT")
    private String responsePayload;

    @Column(length = 100)
    private String errorCode;

    @Column(length = 1000)
    private String errorMessage;

    protected ExecutionHistory() {
    }

    private ExecutionHistory(String executionId, String interfaceCode, ProtocolType protocolType, TriggerType triggerType, String requestPayload) {
        this.executionId = executionId;
        this.interfaceCode = interfaceCode;
        this.protocolType = protocolType;
        this.triggerType = triggerType;
        this.status = ExecutionStatus.RUNNING;
        this.startedAt = LocalDateTime.now();
        this.requestPayload = requestPayload;
    }

    public static ExecutionHistory start(String executionId, String interfaceCode, ProtocolType protocolType, TriggerType triggerType, String requestPayload) {
        return new ExecutionHistory(executionId, interfaceCode, protocolType, triggerType, requestPayload);
    }

    public void markSuccess(String responsePayload, long latencyMillis) {
        this.status = ExecutionStatus.SUCCESS;
        this.responsePayload = responsePayload;
        this.latencyMillis = latencyMillis;
        this.endedAt = LocalDateTime.now();
    }

    public void markFailed(String errorCode, String errorMessage, long latencyMillis) {
        this.status = ExecutionStatus.FAILED;
        this.errorCode = errorCode;
        this.errorMessage = errorMessage;
        this.latencyMillis = latencyMillis;
        this.endedAt = LocalDateTime.now();
    }

    public void markCancelled(String message, long latencyMillis) {
        this.status = ExecutionStatus.CANCELLED;
        this.errorCode = "EXECUTION_CANCELLED";
        this.errorMessage = message;
        this.latencyMillis = latencyMillis;
        this.endedAt = LocalDateTime.now();
    }

    public Long getId() {
        return id;
    }

    public String getExecutionId() {
        return executionId;
    }

    public String getInterfaceCode() {
        return interfaceCode;
    }

    public ProtocolType getProtocolType() {
        return protocolType;
    }

    public TriggerType getTriggerType() {
        return triggerType;
    }

    public ExecutionStatus getStatus() {
        return status;
    }

    public LocalDateTime getStartedAt() {
        return startedAt;
    }

    public LocalDateTime getEndedAt() {
        return endedAt;
    }

    public Long getLatencyMillis() {
        return latencyMillis;
    }

    public String getErrorCode() {
        return errorCode;
    }

    public String getErrorMessage() {
        return errorMessage;
    }

    public String getRequestPayload() {
        return requestPayload;
    }

    public String getResponsePayload() {
        return responsePayload;
    }
}
