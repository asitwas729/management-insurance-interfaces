package com.example.interfacehub.presentation;

import com.example.interfacehub.domain.execution.ExecutionHistory;
import com.example.interfacehub.domain.execution.ExecutionStatus;

public record ExecutionResponse(
    String executionId,
    ExecutionStatus status,
    Long latencyMillis,
    String errorCode,
    String errorMessage
) {
    public static ExecutionResponse from(ExecutionHistory history) {
        return new ExecutionResponse(
            history.getExecutionId(),
            history.getStatus(),
            history.getLatencyMillis(),
            history.getErrorCode(),
            history.getErrorMessage()
        );
    }
}
