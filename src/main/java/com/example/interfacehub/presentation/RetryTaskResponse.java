package com.example.interfacehub.presentation;

import com.example.interfacehub.domain.retry.RetryStatus;
import com.example.interfacehub.domain.retry.RetryTask;
import java.time.LocalDateTime;

public record RetryTaskResponse(
    Long id,
    String interfaceCode,
    String originalExecutionId,
    RetryStatus status,
    String requester,
    String requestReasonCode,
    String requestReasonDetail,
    String approver,
    String rejectReason,
    LocalDateTime createdAt,
    LocalDateTime approvedAt,
    LocalDateTime executedAt
) {
    public static RetryTaskResponse from(RetryTask retryTask) {
        return new RetryTaskResponse(
            retryTask.getId(),
            retryTask.getInterfaceDefinition().getInterfaceCode(),
            retryTask.getOriginalExecutionId(),
            retryTask.getStatus(),
            retryTask.getRequester(),
            retryTask.getRequestReasonCode(),
            retryTask.getRequestReasonDetail(),
            retryTask.getApprover(),
            retryTask.getRejectReason(),
            retryTask.getCreatedAt(),
            retryTask.getApprovedAt(),
            retryTask.getExecutedAt()
        );
    }
}
