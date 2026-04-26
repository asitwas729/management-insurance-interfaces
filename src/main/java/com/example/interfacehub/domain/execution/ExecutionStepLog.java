package com.example.interfacehub.domain.execution;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import java.time.LocalDateTime;

@Entity
public class ExecutionStepLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 100)
    private String executionId;

    @Column(nullable = false)
    private int stepOrder;

    @Column(nullable = false, length = 100)
    private String stepName;

    @Column(nullable = false, length = 50)
    private String stepType;

    @Column(nullable = false)
    private LocalDateTime startTime;

    private LocalDateTime endTime;

    private Long elapsedMs;

    @Column(nullable = false, length = 30)
    private String stepStatus;

    @Column(columnDefinition = "TEXT")
    private String errorMessage;

    protected ExecutionStepLog() {
    }

    private ExecutionStepLog(String executionId, int stepOrder, String stepName, String stepType) {
        this.executionId = executionId;
        this.stepOrder = stepOrder;
        this.stepName = stepName;
        this.stepType = stepType;
        this.startTime = LocalDateTime.now();
        this.stepStatus = "RUNNING";
    }

    public static ExecutionStepLog start(String executionId, int stepOrder, String stepName, String stepType) {
        return new ExecutionStepLog(executionId, stepOrder, stepName, stepType);
    }

    public void markSuccess() {
        this.endTime = LocalDateTime.now();
        this.elapsedMs = java.time.Duration.between(startTime, endTime).toMillis();
        this.stepStatus = "SUCCESS";
    }

    public void markFailed(String errorMessage) {
        this.endTime = LocalDateTime.now();
        this.elapsedMs = java.time.Duration.between(startTime, endTime).toMillis();
        this.stepStatus = "FAILED";
        this.errorMessage = errorMessage;
    }

    public Long getId() {
        return id;
    }

    public String getExecutionId() {
        return executionId;
    }

    public int getStepOrder() {
        return stepOrder;
    }

    public String getStepName() {
        return stepName;
    }

    public String getStepType() {
        return stepType;
    }

    public LocalDateTime getStartTime() {
        return startTime;
    }

    public LocalDateTime getEndTime() {
        return endTime;
    }

    public Long getElapsedMs() {
        return elapsedMs;
    }

    public String getStepStatus() {
        return stepStatus;
    }

    public String getErrorMessage() {
        return errorMessage;
    }
}
