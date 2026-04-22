package com.example.interfacehub.presentation;

import com.example.interfacehub.application.execution.ExecutionHistoryService;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/interfaces/{interfaceCode}/histories")
public class ExecutionHistoryController {

    private final ExecutionHistoryService executionHistoryService;

    public ExecutionHistoryController(ExecutionHistoryService executionHistoryService) {
        this.executionHistoryService = executionHistoryService;
    }

    @GetMapping
    public PagedResponse<ExecutionHistoryResponse> findHistories(
        @PathVariable String interfaceCode,
        @PageableDefault(size = 20, sort = "startedAt", direction = Sort.Direction.DESC) Pageable pageable
    ) {
        return PagedResponse.from(
            executionHistoryService.findHistories(interfaceCode, pageable)
                .map(ExecutionHistoryResponse::from)
        );
    }
}
