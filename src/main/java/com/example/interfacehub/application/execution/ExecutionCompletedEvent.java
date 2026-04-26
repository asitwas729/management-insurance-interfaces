package com.example.interfacehub.application.execution;

import org.springframework.context.ApplicationEvent;

public class ExecutionCompletedEvent extends ApplicationEvent {

    private final String interfaceCode;
    private final String executionId;
    private final String status;
    private final String errorCode;
    private final long latencyMillis;

    public ExecutionCompletedEvent(
        Object source,
        String interfaceCode,
        String executionId,
        String status,
        String errorCode,
        long latencyMillis
    ) {
        super(source);
        this.interfaceCode = interfaceCode;
        this.executionId = executionId;
        this.status = status;
        this.errorCode = errorCode;
        this.latencyMillis = latencyMillis;
    }

    public String getInterfaceCode() {
        return interfaceCode;
    }

    public String getExecutionId() {
        return executionId;
    }

    public String getStatus() {
        return status;
    }

    public String getErrorCode() {
        return errorCode;
    }

    public long getLatencyMillis() {
        return latencyMillis;
    }
}

