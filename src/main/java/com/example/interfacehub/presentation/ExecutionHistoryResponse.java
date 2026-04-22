package com.example.interfacehub.presentation;

import com.example.interfacehub.domain.execution.ExecutionHistory;
import com.example.interfacehub.domain.execution.ExecutionStatus;
import com.example.interfacehub.domain.execution.TriggerType;
import com.example.interfacehub.domain.interfaceconfig.ProtocolType;
import java.time.LocalDateTime;

public record ExecutionHistoryResponse(
    String executionId,
    String interfaceCode,
    ProtocolType protocolType,
    TriggerType triggerType,
    ExecutionStatus status,
    LocalDateTime startedAt,
    LocalDateTime endedAt,
    Long latencyMillis,
    String errorCode,
    String errorMessage
) {
    public static ExecutionHistoryResponse from(ExecutionHistory history) {
        return new ExecutionHistoryResponse(
            history.getExecutionId(),
            history.getInterfaceCode(),
            history.getProtocolType(),
            history.getTriggerType(),
            history.getStatus(),
            history.getStartedAt(),
            history.getEndedAt(),
            history.getLatencyMillis(),
            history.getErrorCode(),
            history.getErrorMessage()
        );
    }
}
