package com.example.interfacehub.presentation;

import com.example.interfacehub.application.execution.ExecutionOrchestrator;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/interfaces/{interfaceCode}/execute")
@Tag(name = "Execution", description = "Manual interface execution APIs")
public class ExecutionController {

    private final ExecutionOrchestrator executionOrchestrator;

    public ExecutionController(ExecutionOrchestrator executionOrchestrator) {
        this.executionOrchestrator = executionOrchestrator;
    }

    @PostMapping
    @Operation(summary = "Execute interface manually", description = "Triggers manual execution for a given interface")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Execution started"),
        @ApiResponse(responseCode = "400", description = "Invalid request"),
        @ApiResponse(responseCode = "404", description = "Interface not found")
    })
    public ExecutionResponse executeManually(
        @PathVariable String interfaceCode,
        @Valid @RequestBody ExecuteInterfaceRequest request
    ) {
        return ExecutionResponse.from(executionOrchestrator.executeManually(interfaceCode, request));
    }
}
