package com.example.interfacehub.presentation;

import com.example.interfacehub.application.execution.ExecutionHistorySearchService;
import com.example.interfacehub.application.execution.ExecutionHistorySearchService.ExecutionHistorySearchCriteria;
import com.example.interfacehub.domain.execution.ExecutionStatus;
import com.example.interfacehub.domain.execution.TriggerType;
import com.example.interfacehub.domain.interfaceconfig.ProtocolType;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.time.LocalDateTime;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/histories")
@Tag(name = "Execution History Search", description = "Cross-interface execution history search APIs")
public class ExecutionHistorySearchController {

    private final ExecutionHistorySearchService executionHistorySearchService;

    public ExecutionHistorySearchController(ExecutionHistorySearchService executionHistorySearchService) {
        this.executionHistorySearchService = executionHistorySearchService;
    }

    @GetMapping("/search")
    @Operation(summary = "Search execution histories", description = "Searches execution histories with optional filters across interfaces")
    public PagedResponse<ExecutionHistoryResponse> search(
        @RequestParam(required = false) String interfaceCode,
        @RequestParam(required = false) String executionIdContains,
        @RequestParam(required = false) ProtocolType protocolType,
        @RequestParam(required = false) TriggerType triggerType,
        @RequestParam(required = false) ExecutionStatus status,
        @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime fromAt,
        @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime toAt,
        @RequestParam(required = false) Long latencyMin,
        @RequestParam(required = false) Long latencyMax,
        @RequestParam(required = false) String errorCode,
        @RequestParam(required = false) String errorMessageContains,
        @PageableDefault(size = 50, sort = "startedAt", direction = Sort.Direction.DESC) Pageable pageable
    ) {
        ExecutionHistorySearchCriteria criteria = new ExecutionHistorySearchCriteria(
            interfaceCode,
            executionIdContains,
            protocolType,
            triggerType,
            status,
            fromAt,
            toAt,
            latencyMin,
            latencyMax,
            errorCode,
            errorMessageContains
        );
        return PagedResponse.from(
            executionHistorySearchService.search(criteria, pageable).map(ExecutionHistoryResponse::from)
        );
    }
}

