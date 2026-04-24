package com.example.interfacehub.presentation;

import com.example.interfacehub.application.dashboard.DashboardSummaryService.DashboardSummary;
import com.example.interfacehub.application.dashboard.DashboardSummaryService.TopFailureInterface;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

public record DashboardSummaryResponse(
    LocalDateTime generatedAt,
    int windowHours,
    long totalInterfaces,
    long activeInterfaces,
    long inactiveInterfaces,
    ExecutionWindow executions,
    long slaBreaches,
    Map<String, Long> retryTasks,
    long dlqTotal,
    long dlqRecent,
    Map<String, Long> dlqReplayRequests,
    List<TopFailureInterfaceRow> topFailures
) {
    public static DashboardSummaryResponse from(DashboardSummary summary) {
        return new DashboardSummaryResponse(
            summary.generatedAt(),
            summary.windowHours(),
            summary.totalInterfaces(),
            summary.activeInterfaces(),
            summary.inactiveInterfaces(),
            new ExecutionWindow(
                summary.executions().total(),
                summary.executions().success(),
                summary.executions().failed(),
                summary.executions().timeout(),
                summary.executions().cancelled(),
                summary.executions().successRate()
            ),
            summary.slaBreaches(),
            summary.retryTasks().counts(),
            summary.dlqTotal(),
            summary.dlqRecent(),
            summary.dlqReplayRequests().counts(),
            summary.topFailures().stream().map(TopFailureInterfaceRow::from).toList()
        );
    }

    public record ExecutionWindow(
        long total,
        long success,
        long failed,
        long timeout,
        long cancelled,
        double successRate
    ) {
    }

    public record TopFailureInterfaceRow(
        String interfaceCode,
        long total,
        long failed,
        long timeout,
        long cancelled,
        long failures,
        double failureRate
    ) {
        public static TopFailureInterfaceRow from(TopFailureInterface value) {
            return new TopFailureInterfaceRow(
                value.interfaceCode(),
                value.total(),
                value.failed(),
                value.timeout(),
                value.cancelled(),
                value.failures(),
                value.failureRate()
            );
        }
    }
}

