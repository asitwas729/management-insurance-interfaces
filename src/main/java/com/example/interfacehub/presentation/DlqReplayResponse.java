package com.example.interfacehub.presentation;

import com.example.interfacehub.domain.execution.ExecutionHistory;

public record DlqReplayResponse(
    Long dlqId,
    ExecutionResponse execution
) {
    public static DlqReplayResponse from(Long dlqId, ExecutionHistory history) {
        return new DlqReplayResponse(dlqId, ExecutionResponse.from(history));
    }
}
