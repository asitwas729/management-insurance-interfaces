package com.example.interfacehub.presentation;

import com.example.interfacehub.application.execution.ExecutionOrchestrator;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/interfaces/{interfaceCode}/execute")
public class ExecutionController {

    private final ExecutionOrchestrator executionOrchestrator;

    public ExecutionController(ExecutionOrchestrator executionOrchestrator) {
        this.executionOrchestrator = executionOrchestrator;
    }

    @PostMapping
    public ExecutionResponse executeManually(
        @PathVariable String interfaceCode,
        @Valid @RequestBody ExecuteInterfaceRequest request
    ) {
        return ExecutionResponse.from(executionOrchestrator.executeManually(interfaceCode, request));
    }
}
