package com.example.interfacehub.presentation;

import com.example.interfacehub.domain.mq.DlqReplayRequest;
import java.time.LocalDateTime;

public record DlqReplayRequestResponse(
    Long id,
    Long dlqId,
    String interfaceCode,
    String dlqReason,
    String status,
    String requester,
    String requestReasonCode,
    String requestReasonDetail,
    String approver,
    String rejectReason,
    LocalDateTime createdAt,
    LocalDateTime approvedAt,
    LocalDateTime executedAt
) {
    public static DlqReplayRequestResponse from(DlqReplayRequest request) {
        return new DlqReplayRequestResponse(
            request.getId(),
            request.getDlqMessage().getId(),
            request.getDlqMessage().getInterfaceCode(),
            request.getDlqMessage().getReason(),
            request.getStatus().name(),
            request.getRequester(),
            request.getRequestReasonCode(),
            request.getRequestReasonDetail(),
            request.getApprover(),
            request.getRejectReason(),
            request.getCreatedAt(),
            request.getApprovedAt(),
            request.getExecutedAt()
        );
    }
}
