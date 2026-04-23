package com.example.interfacehub.presentation;

import com.example.interfacehub.domain.execution.ExecutionHistory;
import com.example.interfacehub.domain.execution.ExecutionStatus;
import com.example.interfacehub.domain.execution.TriggerType;
import com.example.interfacehub.domain.interfaceconfig.ProtocolType;
import java.time.LocalDateTime;

public record ExecutionResponse(
    String executionId,
    ExecutionStatus status,
    ProtocolType protocolType,
    TriggerType triggerType,
    LocalDateTime startedAt,
    LocalDateTime endedAt,
    Long latencyMillis,
    String errorCode,
    String errorMessage
) {
    public static ExecutionResponse from(ExecutionHistory history) {
        return new ExecutionResponse(
            history.getExecutionId(),
            history.getStatus(),
            history.getProtocolType(),
            history.getTriggerType(),
            history.getStartedAt(),
            history.getEndedAt(),
            history.getLatencyMillis(),
            history.getErrorCode(),
            history.getErrorMessage()
        );
    }
}
