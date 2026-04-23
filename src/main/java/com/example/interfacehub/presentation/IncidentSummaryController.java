package com.example.interfacehub.presentation;

import com.example.interfacehub.application.incident.IncidentSummaryService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.concurrent.CompletableFuture;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/incidents")
@Tag(name = "Incidents", description = "Incident summary APIs")
public class IncidentSummaryController {

    private final IncidentSummaryService incidentSummaryService;

    public IncidentSummaryController(IncidentSummaryService incidentSummaryService) {
        this.incidentSummaryService = incidentSummaryService;
    }

    @GetMapping("/summary")
    @Operation(summary = "Get incident summary", description = "Returns AI summary for recent incident executions")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Summary returned"),
        @ApiResponse(responseCode = "400", description = "Invalid query params"),
        @ApiResponse(responseCode = "403", description = "Forbidden")
    })
    public CompletableFuture<IncidentSummaryResponse> getSummary(
        @RequestParam(defaultValue = "24") int hoursBack,
        @RequestParam(defaultValue = "100") int limit
    ) {
        return incidentSummaryService.summarize(hoursBack, limit);
    }
}
