package com.example.interfacehub.presentation.response;

import com.example.interfacehub.application.dashboard.DashboardSummaryService;

public record SlaBreachSummaryResponse(
    String interfaceCode,
    long slaMillis,
    long slaBreachCount,
    double avgLatencyMs
) {
    public static SlaBreachSummaryResponse from(DashboardSummaryService.SlaBreachStat stat) {
        return new SlaBreachSummaryResponse(
            stat.interfaceCode(),
            stat.slaMillis(),
            stat.slaBreachCount(),
            stat.avgLatencyMs()
        );
    }
}

