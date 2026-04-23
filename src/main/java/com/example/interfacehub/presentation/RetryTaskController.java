package com.example.interfacehub.presentation;

import com.example.interfacehub.application.retry.RetryTaskService;
import com.example.interfacehub.domain.retry.RetryStatus;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1")
@Tag(name = "Retry Tasks", description = "Retry request lifecycle APIs")
public class RetryTaskController {

    private final RetryTaskService retryTaskService;

    public RetryTaskController(RetryTaskService retryTaskService) {
        this.retryTaskService = retryTaskService;
    }

    @PostMapping("/interfaces/{interfaceCode}/retries")
    @Operation(summary = "Create retry task", description = "Creates a retry task for a failed interface execution")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Retry task created"),
        @ApiResponse(responseCode = "400", description = "Invalid request"),
        @ApiResponse(responseCode = "404", description = "Interface not found")
    })
    public RetryTaskResponse requestRetry(
        @PathVariable String interfaceCode,
        @Valid @RequestBody CreateRetryTaskRequest request
    ) {
        return RetryTaskResponse.from(retryTaskService.requestRetry(interfaceCode, request));
    }

    @PostMapping("/retries/{retryTaskId}/approve")
    @Operation(summary = "Approve retry task", description = "Approves a pending retry task")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Retry approved"),
        @ApiResponse(responseCode = "403", description = "Forbidden"),
        @ApiResponse(responseCode = "404", description = "Retry task not found")
    })
    public RetryTaskResponse approveRetry(
        @PathVariable Long retryTaskId,
        @Valid @RequestBody ApproveRetryTaskRequest request
    ) {
        return RetryTaskResponse.from(retryTaskService.approveRetry(retryTaskId, request));
    }

    @PostMapping("/retries/{retryTaskId}/reject")
    @Operation(summary = "Reject retry task", description = "Rejects a pending retry task")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Retry rejected"),
        @ApiResponse(responseCode = "403", description = "Forbidden"),
        @ApiResponse(responseCode = "404", description = "Retry task not found")
    })
    public RetryTaskResponse rejectRetry(
        @PathVariable Long retryTaskId,
        @Valid @RequestBody RejectRetryTaskRequest request
    ) {
        return RetryTaskResponse.from(retryTaskService.rejectRetry(retryTaskId, request));
    }

    @PostMapping("/retries/{retryTaskId}/execute")
    @Operation(summary = "Execute retry task", description = "Executes an approved retry task")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Retry executed"),
        @ApiResponse(responseCode = "403", description = "Forbidden"),
        @ApiResponse(responseCode = "404", description = "Retry task not found")
    })
    public ExecutionResponse executeRetry(@PathVariable Long retryTaskId) {
        return ExecutionResponse.from(retryTaskService.executeApprovedRetry(retryTaskId));
    }

    @GetMapping("/retries/{retryTaskId}")
    @Operation(summary = "Get retry task", description = "Returns retry task detail")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Retry task returned"),
        @ApiResponse(responseCode = "404", description = "Retry task not found")
    })
    public RetryTaskResponse findRetryTask(@PathVariable Long retryTaskId) {
        return RetryTaskResponse.from(retryTaskService.findById(retryTaskId));
    }

    @GetMapping("/retries")
    @Operation(summary = "List retry tasks", description = "Returns paginated retry tasks filtered by status")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Retry tasks returned"),
        @ApiResponse(responseCode = "401", description = "Unauthorized")
    })
    public PagedResponse<RetryTaskResponse> findRetryTasks(
        @RequestParam(required = false) RetryStatus status,
        @PageableDefault(size = 20, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable
    ) {
        return PagedResponse.from(
            retryTaskService.findByStatusPaged(status, pageable).map(RetryTaskResponse::from)
        );
    }
}
