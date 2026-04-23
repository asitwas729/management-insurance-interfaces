package com.example.interfacehub.presentation;

import com.example.interfacehub.application.scheduler.RetentionService;
import com.example.interfacehub.application.scheduler.RetentionService.RetentionResult;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/admin")
@Tag(name = "Admin", description = "Administrative operations")
public class AdminController {

    private final RetentionService retentionService;

    public AdminController(RetentionService retentionService) {
        this.retentionService = retentionService;
    }

    @PostMapping("/archive")
    @Operation(summary = "Trigger archive and purge", description = "Starts retention archive/purge job immediately")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Archive job completed"),
        @ApiResponse(responseCode = "401", description = "Unauthorized"),
        @ApiResponse(responseCode = "403", description = "Forbidden")
    })
    public RetentionResult triggerArchive() {
        return retentionService.archiveAndPurge();
    }
}
