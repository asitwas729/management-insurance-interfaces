package com.example.interfacehub.presentation;

import com.example.interfacehub.domain.mq.DlqMessage;
import java.time.LocalDateTime;

public record DlqMessageResponse(
    Long id,
    String interfaceCode,
    String topic,
    String reason,
    int replayCount,
    LocalDateTime lastReplayedAt,
    LocalDateTime createdAt
) {
    public static DlqMessageResponse from(DlqMessage message) {
        return new DlqMessageResponse(
            message.getId(),
            message.getInterfaceCode(),
            message.getTopic(),
            message.getReason(),
            message.getReplayCount(),
            message.getLastReplayedAt(),
            message.getCreatedAt()
        );
    }
}
