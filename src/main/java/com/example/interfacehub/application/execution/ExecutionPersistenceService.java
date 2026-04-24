package com.example.interfacehub.application.execution;

import com.example.interfacehub.common.error.BusinessException;
import com.example.interfacehub.common.error.ErrorCode;
import com.example.interfacehub.domain.execution.ExecutionHistory;
import com.example.interfacehub.domain.execution.IdempotencyRecord;
import com.example.interfacehub.domain.execution.TriggerType;
import com.example.interfacehub.domain.interfaceconfig.ProtocolType;
import com.example.interfacehub.infrastructure.persistence.ExecutionHistoryRepository;
import com.example.interfacehub.infrastructure.persistence.IdempotencyRecordRepository;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ExecutionPersistenceService {

    private final ExecutionHistoryRepository executionHistoryRepository;
    private final IdempotencyRecordRepository idempotencyRecordRepository;

    public ExecutionPersistenceService(
        ExecutionHistoryRepository executionHistoryRepository,
        IdempotencyRecordRepository idempotencyRecordRepository
    ) {
        this.executionHistoryRepository = executionHistoryRepository;
        this.idempotencyRecordRepository = idempotencyRecordRepository;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void reserveIdempotencyKey(String idempotencyKey, String interfaceCode, String executionId) {
        if (idempotencyRecordRepository.existsByIdempotencyKey(idempotencyKey)) {
            throw new BusinessException(ErrorCode.DUPLICATE_REQUEST);
        }

        try {
            idempotencyRecordRepository.save(IdempotencyRecord.reserve(idempotencyKey, interfaceCode, executionId));
        } catch (DataIntegrityViolationException exception) {
            throw new BusinessException(ErrorCode.DUPLICATE_REQUEST);
        }
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public ExecutionHistory createRunningHistory(
        String executionId,
        String interfaceCode,
        ProtocolType protocolType,
        TriggerType triggerType,
        String requestPayload
    ) {
        ExecutionHistory history = ExecutionHistory.start(
            executionId,
            interfaceCode,
            protocolType,
            triggerType,
            requestPayload
        );
        return executionHistoryRepository.save(history);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public ExecutionHistory markSuccess(Long historyId, String responsePayload, long latencyMillis) {
        ExecutionHistory history = executionHistoryRepository.findById(historyId)
            .orElseThrow(() -> new BusinessException(ErrorCode.EXECUTION_NOT_FOUND));
        history.markSuccess(responsePayload, latencyMillis);
        idempotencyRecordRepository.findByExecutionId(history.getExecutionId()).ifPresent(IdempotencyRecord::markSuccess);
        return history;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public ExecutionHistory markFailed(Long historyId, String errorCode, String errorMessage, long latencyMillis) {
        ExecutionHistory history = executionHistoryRepository.findById(historyId)
            .orElseThrow(() -> new BusinessException(ErrorCode.EXECUTION_NOT_FOUND));
        history.markFailed(errorCode, errorMessage, latencyMillis);
        idempotencyRecordRepository.findByExecutionId(history.getExecutionId()).ifPresent(IdempotencyRecord::markFailed);
        return history;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public ExecutionHistory markCancelledByExecutionId(String executionId, String message) {
        ExecutionHistory history = executionHistoryRepository.findByExecutionId(executionId)
            .orElseThrow(() -> new BusinessException(ErrorCode.EXECUTION_NOT_FOUND));
        long latency = history.getStartedAt() == null
            ? 0L
            : java.time.Duration.between(history.getStartedAt(), java.time.LocalDateTime.now()).toMillis();
        history.markCancelled(message, Math.max(latency, 0L));
        idempotencyRecordRepository.findByExecutionId(history.getExecutionId()).ifPresent(IdempotencyRecord::markFailed);
        return history;
    }
}
