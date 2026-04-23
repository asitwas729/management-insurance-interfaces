package com.example.interfacehub.application.retry;

import com.example.interfacehub.application.audit.AuditLogService;
import com.example.interfacehub.application.execution.ExecutionOrchestrator;
import com.example.interfacehub.application.registry.InterfaceRegistryService;
import com.example.interfacehub.common.error.BusinessException;
import com.example.interfacehub.common.error.ErrorCode;
import com.example.interfacehub.domain.execution.ExecutionHistory;
import com.example.interfacehub.domain.execution.ExecutionStatus;
import com.example.interfacehub.domain.interfaceconfig.InterfaceDefinition;
import com.example.interfacehub.domain.retry.RetryStatus;
import com.example.interfacehub.domain.retry.RetryTask;
import com.example.interfacehub.infrastructure.persistence.ExecutionHistoryRepository;
import com.example.interfacehub.infrastructure.persistence.RetryTaskRepository;
import com.example.interfacehub.presentation.ApproveRetryTaskRequest;
import com.example.interfacehub.presentation.CreateRetryTaskRequest;
import com.example.interfacehub.presentation.RejectRetryTaskRequest;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import java.util.Map;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

@Service
public class RetryTaskService {

    private final RetryTaskRepository retryTaskRepository;
    private final ExecutionHistoryRepository executionHistoryRepository;
    private final InterfaceRegistryService interfaceRegistryService;
    private final ExecutionOrchestrator executionOrchestrator;
    private final AuditLogService auditLogService;
    private final ObjectMapper objectMapper;
    private final TransactionTemplate transactionTemplate;

    public RetryTaskService(
        RetryTaskRepository retryTaskRepository,
        ExecutionHistoryRepository executionHistoryRepository,
        InterfaceRegistryService interfaceRegistryService,
        ExecutionOrchestrator executionOrchestrator,
        AuditLogService auditLogService,
        ObjectMapper objectMapper,
        PlatformTransactionManager transactionManager
    ) {
        this.retryTaskRepository = retryTaskRepository;
        this.executionHistoryRepository = executionHistoryRepository;
        this.interfaceRegistryService = interfaceRegistryService;
        this.executionOrchestrator = executionOrchestrator;
        this.auditLogService = auditLogService;
        this.objectMapper = objectMapper;
        this.transactionTemplate = new TransactionTemplate(transactionManager);
    }

    @Transactional
    public RetryTask requestRetry(String interfaceCode, CreateRetryTaskRequest request) {
        InterfaceDefinition definition = interfaceRegistryService.findByCode(interfaceCode);
        ExecutionHistory original = executionHistoryRepository.findByExecutionId(request.originalExecutionId())
            .orElseThrow(() -> new BusinessException(ErrorCode.EXECUTION_NOT_FOUND));

        if (original.getStatus() != ExecutionStatus.FAILED && original.getStatus() != ExecutionStatus.TIMEOUT) {
            throw new BusinessException(ErrorCode.RETRY_INVALID_STATUS, "Only FAILED or TIMEOUT execution can be retried");
        }

        RetryTask retryTask = RetryTask.request(
            definition,
            request.originalExecutionId(),
            request.requester(),
            request.reasonCode(),
            request.reasonDetail()
        );
        RetryTask saved = retryTaskRepository.save(retryTask);

        auditLogService.record(
            request.requester(),
            "REQUEST_RETRY",
            "RETRY_TASK",
            String.valueOf(saved.getId()),
            null,
            "{\"status\":\"PENDING\",\"reasonCode\":\"" + jsonEscape(request.reasonCode()) + "\"}"
        );
        return saved;
    }

    @Transactional
    public RetryTask approveRetry(Long retryTaskId, ApproveRetryTaskRequest request) {
        RetryTask retryTask = findById(retryTaskId);
        if (retryTask.getStatus() != RetryStatus.PENDING) {
            throw new BusinessException(ErrorCode.RETRY_INVALID_STATUS, "Only PENDING retry task can be approved");
        }

        retryTask.approve(request.approver());

        auditLogService.record(
            request.approver(),
            "APPROVE_RETRY",
            "RETRY_TASK",
            String.valueOf(retryTask.getId()),
            "{\"status\":\"PENDING\"}",
            "{\"status\":\"APPROVED\"}"
        );
        return retryTask;
    }

    @Transactional
    public RetryTask rejectRetry(Long retryTaskId, RejectRetryTaskRequest request) {
        RetryTask retryTask = findById(retryTaskId);
        if (retryTask.getStatus() != RetryStatus.PENDING) {
            throw new BusinessException(ErrorCode.RETRY_INVALID_STATUS, "Only PENDING retry task can be rejected");
        }

        retryTask.reject(request.approver(), request.reason());

        auditLogService.record(
            request.approver(),
            "REJECT_RETRY",
            "RETRY_TASK",
            String.valueOf(retryTask.getId()),
            "{\"status\":\"PENDING\"}",
            "{\"status\":\"REJECTED\",\"reason\":\"" + jsonEscape(request.reason()) + "\"}"
        );
        return retryTask;
    }

    public ExecutionHistory executeApprovedRetry(Long retryTaskId) {
        RetryTask retryTask = findById(retryTaskId);
        if (retryTask.getStatus() != RetryStatus.APPROVED) {
            throw new BusinessException(ErrorCode.RETRY_NOT_APPROVED);
        }

        ExecutionHistory original = executionHistoryRepository.findByExecutionId(retryTask.getOriginalExecutionId())
            .orElseThrow(() -> new BusinessException(ErrorCode.EXECUTION_NOT_FOUND));

        Map<String, Object> payload = toPayloadMap(original.getRequestPayload());
        String idempotencyKey = "RETRY-" + retryTask.getId() + "-" + System.currentTimeMillis();

        ExecutionHistory retried = executionOrchestrator.executeRetry(
            retryTask.getInterfaceDefinition().getInterfaceCode(),
            idempotencyKey,
            payload
        );

        transactionTemplate.executeWithoutResult(status -> finalizeRetryStatusAndAudit(retryTaskId, retried.getStatus()));
        return retried;
    }

    @Transactional(readOnly = true)
    public RetryTask findById(Long retryTaskId) {
        return retryTaskRepository.findById(retryTaskId)
            .orElseThrow(() -> new BusinessException(ErrorCode.RETRY_TASK_NOT_FOUND));
    }

    @Transactional(readOnly = true)
    public List<RetryTask> findByStatus(RetryStatus status) {
        if (status == null) {
            return retryTaskRepository.findAll();
        }
        return retryTaskRepository.findByStatus(status);
    }

    @Transactional(readOnly = true)
    public Page<RetryTask> findByStatusPaged(RetryStatus status, Pageable pageable) {
        if (status == null) {
            return retryTaskRepository.findAllByOrderByCreatedAtDesc(pageable);
        }
        return retryTaskRepository.findByStatusOrderByCreatedAtDesc(status, pageable);
    }

    private Map<String, Object> toPayloadMap(String requestPayload) {
        if (requestPayload == null || requestPayload.isBlank()) {
            return Map.of();
        }
        try {
            return objectMapper.readValue(requestPayload, new TypeReference<>() {
            });
        } catch (JsonProcessingException exception) {
            return Map.of();
        }
    }

    private void finalizeRetryStatusAndAudit(Long retryTaskId, ExecutionStatus executionStatus) {
        RetryTask retryTask = retryTaskRepository.findById(retryTaskId)
            .orElseThrow(() -> new BusinessException(ErrorCode.RETRY_TASK_NOT_FOUND));

        if (executionStatus == ExecutionStatus.SUCCESS) {
            retryTask.markExecuted();
            auditLogService.record(
                "system",
                "EXECUTE_RETRY",
                "RETRY_TASK",
                String.valueOf(retryTask.getId()),
                "{\"status\":\"APPROVED\"}",
                "{\"status\":\"EXECUTED\"}"
            );
            return;
        }

        retryTask.markFailed();
        auditLogService.record(
            "system",
            "EXECUTE_RETRY",
            "RETRY_TASK",
            String.valueOf(retryTask.getId()),
            "{\"status\":\"APPROVED\"}",
            "{\"status\":\"FAILED\"}"
        );
    }

    private String jsonEscape(String value) {
        return value.replace("\\", "\\\\").replace("\"", "\\\"");
    }
}
