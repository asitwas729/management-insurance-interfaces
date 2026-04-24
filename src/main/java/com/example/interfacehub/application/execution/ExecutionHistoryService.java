package com.example.interfacehub.application.execution;

import com.example.interfacehub.common.error.BusinessException;
import com.example.interfacehub.common.error.ErrorCode;
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

    @Transactional(readOnly = true)
    public ExecutionHistory findByExecutionId(String interfaceCode, String executionId) {
        ExecutionHistory history = executionHistoryRepository.findByExecutionId(executionId)
            .orElseThrow(() -> new BusinessException(ErrorCode.EXECUTION_NOT_FOUND));
        if (!history.getInterfaceCode().equals(interfaceCode)) {
            throw new BusinessException(ErrorCode.EXECUTION_NOT_FOUND);
        }
        return history;
    }

    /**
     * Primary key (Long id)로 ExecutionHistory를 조회합니다.
     */
    @Transactional(readOnly = true)
    public ExecutionHistory findById(Long id) {
        return executionHistoryRepository.findById(id)
            .orElseThrow(() -> new BusinessException(ErrorCode.EXECUTION_NOT_FOUND));
    }
}
