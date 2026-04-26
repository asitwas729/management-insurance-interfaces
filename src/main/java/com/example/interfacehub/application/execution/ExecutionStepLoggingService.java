package com.example.interfacehub.application.execution;

import com.example.interfacehub.domain.execution.ExecutionStepLog;
import com.example.interfacehub.infrastructure.persistence.ExecutionStepLogRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ExecutionStepLoggingService {

    private final ExecutionStepLogRepository repository;

    public ExecutionStepLoggingService(ExecutionStepLogRepository repository) {
        this.repository = repository;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public Long startStep(String executionId, int stepOrder, String stepName, String stepType) {
        ExecutionStepLog log = ExecutionStepLog.start(executionId, stepOrder, stepName, stepType);
        return repository.save(log).getId();
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void markStepSuccess(Long stepLogId) {
        repository.findById(stepLogId).ifPresent(log -> {
            log.markSuccess();
            repository.save(log);
        });
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void markStepFailed(Long stepLogId, String errorMessage) {
        repository.findById(stepLogId).ifPresent(log -> {
            log.markFailed(errorMessage);
            repository.save(log);
        });
    }
}
