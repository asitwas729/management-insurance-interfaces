package com.example.interfacehub.presentation;

import com.example.interfacehub.application.dashboard.DashboardSummaryService;
import com.example.interfacehub.application.dashboard.DashboardSummaryService.DashboardSummary;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/dashboard")
@Tag(name = "Dashboard", description = "Dashboard summary APIs")
public class DashboardController {

    private final DashboardSummaryService dashboardSummaryService;

    public DashboardController(DashboardSummaryService dashboardSummaryService) {
        this.dashboardSummaryService = dashboardSummaryService;
    }

    @GetMapping("/summary")
    @Operation(summary = "Get dashboard summary", description = "Returns cross-interface monitoring summary for recent executions")
    public DashboardSummaryResponse summary(@RequestParam(defaultValue = "24") int windowHours) {
        DashboardSummary summary = dashboardSummaryService.summary(windowHours);
        return DashboardSummaryResponse.from(summary);
    }
}

