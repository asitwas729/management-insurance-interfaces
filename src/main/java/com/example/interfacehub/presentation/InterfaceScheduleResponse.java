package com.example.interfacehub.presentation;

import com.example.interfacehub.domain.scheduler.InterfaceSchedule;
import java.time.LocalDateTime;

public record InterfaceScheduleResponse(
    Long id,
    String interfaceCode,
    String cronExpression,
    boolean enabled,
    String payloadTemplate,
    LocalDateTime createdAt,
    LocalDateTime updatedAt
) {
    public static InterfaceScheduleResponse from(InterfaceSchedule s) {
        return new InterfaceScheduleResponse(
            s.getId(), s.getInterfaceCode(), s.getCronExpression(),
            s.isEnabled(), s.getPayloadTemplate(), s.getCreatedAt(), s.getUpdatedAt()
        );
    }
}
