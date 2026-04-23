package com.example.interfacehub.presentation;

import com.example.interfacehub.application.incident.IncidentSummaryService;
import java.util.concurrent.CompletableFuture;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/incidents")
public class IncidentSummaryController {

    private final IncidentSummaryService incidentSummaryService;

    public IncidentSummaryController(IncidentSummaryService incidentSummaryService) {
        this.incidentSummaryService = incidentSummaryService;
    }

    @GetMapping("/summary")
    public CompletableFuture<IncidentSummaryResponse> getSummary(
        @RequestParam(defaultValue = "24") int hoursBack,
        @RequestParam(defaultValue = "100") int limit
    ) {
        return incidentSummaryService.summarize(hoursBack, limit);
    }
}
