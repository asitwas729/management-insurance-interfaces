package com.example.interfacehub.presentation;

import java.time.LocalDateTime;

public record IncidentSummaryResponse(
    String summary,
    int analyzedCount,
    int hoursBack,
    LocalDateTime generatedAt
) {
    public static IncidentSummaryResponse disabled(int analyzedCount, int hoursBack) {
        return new IncidentSummaryResponse(
            "LLM summary is disabled. Set LLM_ENABLED=true and ANTHROPIC_API_KEY to activate.",
            analyzedCount,
            hoursBack,
            LocalDateTime.now()
        );
    }
}
