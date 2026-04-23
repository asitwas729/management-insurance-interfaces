package com.example.interfacehub.presentation;

import com.example.interfacehub.application.retry.RetryTaskService;
import com.example.interfacehub.domain.retry.RetryStatus;
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
public class RetryTaskController {

    private final RetryTaskService retryTaskService;

    public RetryTaskController(RetryTaskService retryTaskService) {
        this.retryTaskService = retryTaskService;
    }

    @PostMapping("/interfaces/{interfaceCode}/retries")
    public RetryTaskResponse requestRetry(
        @PathVariable String interfaceCode,
        @Valid @RequestBody CreateRetryTaskRequest request
    ) {
        return RetryTaskResponse.from(retryTaskService.requestRetry(interfaceCode, request));
    }

    @PostMapping("/retries/{retryTaskId}/approve")
    public RetryTaskResponse approveRetry(
        @PathVariable Long retryTaskId,
        @Valid @RequestBody ApproveRetryTaskRequest request
    ) {
        return RetryTaskResponse.from(retryTaskService.approveRetry(retryTaskId, request));
    }

    @PostMapping("/retries/{retryTaskId}/reject")
    public RetryTaskResponse rejectRetry(
        @PathVariable Long retryTaskId,
        @Valid @RequestBody RejectRetryTaskRequest request
    ) {
        return RetryTaskResponse.from(retryTaskService.rejectRetry(retryTaskId, request));
    }

    @PostMapping("/retries/{retryTaskId}/execute")
    public ExecutionResponse executeRetry(@PathVariable Long retryTaskId) {
        return ExecutionResponse.from(retryTaskService.executeApprovedRetry(retryTaskId));
    }

    @GetMapping("/retries/{retryTaskId}")
    public RetryTaskResponse findRetryTask(@PathVariable Long retryTaskId) {
        return RetryTaskResponse.from(retryTaskService.findById(retryTaskId));
    }

    @GetMapping("/retries")
    public PagedResponse<RetryTaskResponse> findRetryTasks(
        @RequestParam(required = false) RetryStatus status,
        @PageableDefault(size = 20, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable
    ) {
        return PagedResponse.from(
            retryTaskService.findByStatusPaged(status, pageable).map(RetryTaskResponse::from)
        );
    }
}
