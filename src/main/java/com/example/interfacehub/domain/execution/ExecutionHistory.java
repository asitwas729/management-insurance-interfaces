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

    @Column(length = 200)
    private String interfaceName;

    @Column(length = 100)
    private String sourceSystem;

    @Column(length = 100)
    private String targetSystem;

    @Column(length = 100)
    private String partnerName;

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

    @Column(nullable = false)
    private int retryCount = 0;

    @Column(columnDefinition = "TEXT")
    private String requestPayload;

    @Column(columnDefinition = "TEXT")
    private String responsePayload;

    @Column(length = 100)
    private String errorCode;

    @Column(length = 50)
    private String errorCategory;

    @Column(length = 1000)
    private String errorMessage;

    protected ExecutionHistory() {
    }

    private ExecutionHistory(String executionId, String interfaceCode, String interfaceName, ProtocolType protocolType, TriggerType triggerType, String requestPayload) {
        this.executionId = executionId;
        this.interfaceCode = interfaceCode;
        this.interfaceName = interfaceName;
        this.protocolType = protocolType;
        this.triggerType = triggerType;
        this.status = ExecutionStatus.RUNNING;
        this.startedAt = LocalDateTime.now();
        this.requestPayload = requestPayload;
    }

    public static ExecutionHistory start(String executionId, String interfaceCode, String interfaceName, ProtocolType protocolType, TriggerType triggerType, String requestPayload) {
        return new ExecutionHistory(executionId, interfaceCode, interfaceName, protocolType, triggerType, requestPayload);
    }

    public void setSystems(String sourceSystem, String targetSystem, String partnerName) {
        this.sourceSystem = sourceSystem;
        this.targetSystem = targetSystem;
        this.partnerName = partnerName;
    }

    public void setRetryCount(int retryCount) {
        this.retryCount = retryCount;
    }

    public void markSuccess(String responsePayload, long latencyMillis) {
        this.status = ExecutionStatus.SUCCESS;
        this.responsePayload = responsePayload;
        this.latencyMillis = latencyMillis;
        this.endedAt = LocalDateTime.now();
    }

    public void markFailed(String errorCode, String errorCategory, String errorMessage, long latencyMillis) {
        this.status = ExecutionStatus.FAILED;
        this.errorCode = errorCode;
        this.errorCategory = errorCategory;
        this.errorMessage = errorMessage;
        this.latencyMillis = latencyMillis;
        this.endedAt = LocalDateTime.now();
    }

    public void markCancelled(String message, long latencyMillis) {
        this.status = ExecutionStatus.CANCELLED;
        this.errorCode = "EXECUTION_CANCELLED";
        this.errorCategory = "SYSTEM";
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

    public String getInterfaceName() {
        return interfaceName;
    }

    public String getSourceSystem() {
        return sourceSystem;
    }

    public String getTargetSystem() {
        return targetSystem;
    }

    public String getPartnerName() {
        return partnerName;
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

    public int getRetryCount() {
        return retryCount;
    }

    public String getErrorCode() {
        return errorCode;
    }

    public String getErrorCategory() {
        return errorCategory;
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
