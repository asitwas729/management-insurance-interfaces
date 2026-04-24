package com.example.interfacehub.presentation.response;

import com.example.interfacehub.application.dashboard.DashboardSummaryService;
import java.time.LocalDateTime;

public record InterfaceStatResponse(
    String interfaceCode,
    double successRate,
    double avgLatencyMs,
    long slaBreachCount,
    LocalDateTime lastExecutedAt
) {
    public static InterfaceStatResponse from(DashboardSummaryService.InterfaceStat stat) {
        return new InterfaceStatResponse(
            stat.interfaceCode(),
            stat.successRate(),
            stat.avgLatencyMs(),
            stat.slaBreachCount(),
            stat.lastExecutedAt()
        );
    }
}

