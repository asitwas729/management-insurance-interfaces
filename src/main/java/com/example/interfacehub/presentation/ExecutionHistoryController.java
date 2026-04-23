package com.example.interfacehub.presentation;

import com.example.interfacehub.application.execution.ExecutionHistoryService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/interfaces/{interfaceCode}/histories")
@Tag(name = "Execution History", description = "Execution history query APIs")
public class ExecutionHistoryController {

    private final ExecutionHistoryService executionHistoryService;

    public ExecutionHistoryController(ExecutionHistoryService executionHistoryService) {
        this.executionHistoryService = executionHistoryService;
    }

    @GetMapping
    @Operation(summary = "List execution histories", description = "Returns paginated execution histories for an interface")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Histories returned"),
        @ApiResponse(responseCode = "404", description = "Interface not found")
    })
    public PagedResponse<ExecutionHistoryResponse> findHistories(
        @PathVariable String interfaceCode,
        @PageableDefault(size = 20, sort = "startedAt", direction = Sort.Direction.DESC) Pageable pageable
    ) {
        return PagedResponse.from(
            executionHistoryService.findHistories(interfaceCode, pageable)
                .map(ExecutionHistoryResponse::from)
        );
    }

    @GetMapping("/{executionId}")
    @Operation(summary = "Get execution history detail", description = "Returns detail for a single execution id")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "History returned"),
        @ApiResponse(responseCode = "404", description = "Execution history not found")
    })
    public ExecutionHistoryResponse findHistory(
        @PathVariable String interfaceCode,
        @PathVariable String executionId
    ) {
        return ExecutionHistoryResponse.from(
            executionHistoryService.findByExecutionId(interfaceCode, executionId)
        );
    }
}
