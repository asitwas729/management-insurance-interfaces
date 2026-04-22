package com.example.interfacehub.application.execution;

import com.example.interfacehub.domain.execution.ExecutionHistory;
import com.example.interfacehub.infrastructure.persistence.ExecutionHistoryRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ExecutionHistoryService {

    private final ExecutionHistoryRepository executionHistoryRepository;

    public ExecutionHistoryService(ExecutionHistoryRepository executionHistoryRepository) {
        this.executionHistoryRepository = executionHistoryRepository;
    }

    @Transactional(readOnly = true)
    public Page<ExecutionHistory> findHistories(String interfaceCode, Pageable pageable) {
        return executionHistoryRepository.findByInterfaceCode(interfaceCode, pageable);
    }
}
