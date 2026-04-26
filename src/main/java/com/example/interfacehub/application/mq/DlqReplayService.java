package com.example.interfacehub.application.mq;

import com.example.interfacehub.application.audit.AuditLogService;
import com.example.interfacehub.application.execution.ExecutionOrchestrator;
import com.example.interfacehub.application.policy.PolicyExecutionContext;
import com.example.interfacehub.common.error.BusinessException;
import com.example.interfacehub.common.error.ErrorCode;
import com.example.interfacehub.domain.execution.ExecutionHistory;
import com.example.interfacehub.domain.execution.ExecutionStatus;
import com.example.interfacehub.domain.execution.TriggerType;
import com.example.interfacehub.domain.mq.DlqMessage;
import com.example.interfacehub.domain.mq.DlqMessageStatus;
import com.example.interfacehub.domain.mq.DlqReplayRequest;
import com.example.interfacehub.domain.mq.DlqReplayStatus;
import com.example.interfacehub.infrastructure.persistence.DlqReplayRequestRepository;
import com.example.interfacehub.presentation.ApproveDlqReplayRequest;
import com.example.interfacehub.presentation.CreateDlqReplayRequest;
import com.example.interfacehub.presentation.ExecuteDlqReplayRequest;
import com.example.interfacehub.presentation.RejectDlqReplayRequest;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class DlqReplayService {

    private final DlqMessageService dlqMessageService;
    private final DlqReplayRequestRepository dlqReplayRequestRepository;
    private final ObjectMapper objectMapper;
    private final ExecutionOrchestrator executionOrchestrator;
    private final DlqReplayPolicyProperties policyProperties;
    private final AuditLogService auditLogService;

    public DlqReplayService(
        DlqMessageService dlqMessageService,
        DlqReplayRequestRepository dlqReplayRequestRepository,
        ObjectMapper objectMapper,
        ExecutionOrchestrator executionOrchestrator,
        DlqReplayPolicyProperties policyProperties,
        AuditLogService auditLogService
    ) {
        this.dlqMessageService = dlqMessageService;
        this.dlqReplayRequestRepository = dlqReplayRequestRepository;
        this.objectMapper = objectMapper;
        this.executionOrchestrator = executionOrchestrator;
        this.policyProperties = policyProperties;
        this.auditLogService = auditLogService;
    }

    @Transactional
    public DlqReplayRequest requestReplay(Long dlqId, CreateDlqReplayRequest request) {
        DlqMessage message = dlqMessageService.findById(dlqId);
        if (message.getStatus() == DlqMessageStatus.EXHAUSTED || message.exceedsReplayLimit(policyProperties.getMaxAttempts())) {
            message.markExhausted();
            throw new BusinessException(ErrorCode.DLQ_REPLAY_LIMIT_EXCEEDED, "DLQ message is exhausted");
        }
        String payloadOverrideJson = toJson(request.payloadOverride());

        DlqReplayRequest replayRequest = DlqReplayRequest.request(
            message,
            request.requester(),
            request.reasonCode(),
            request.reasonDetail(),
            payloadOverrideJson
        );
        DlqReplayRequest saved = dlqReplayRequestRepository.save(replayRequest);

        auditLogService.record(
            request.requester(),
            "REQUEST_DLQ_REPLAY",
            "DLQ_REPLAY_REQUEST",
            String.valueOf(saved.getId()),
            null,
            "{\"status\":\"PENDING\",\"reasonCode\":\"" + request.reasonCode().replace("\"", "'") + "\"}"
        );
        return saved;
    }

    @Transactional
    public DlqReplayRequest approve(Long replayRequestId, ApproveDlqReplayRequest request) {
        DlqReplayRequest replayRequest = findById(replayRequestId);
        if (replayRequest.getStatus() != DlqReplayStatus.PENDING) {
            throw new BusinessException(ErrorCode.RETRY_INVALID_STATUS, "Only PENDING replay request can be approved");
        }
        replayRequest.approve(request.approver());

        auditLogService.record(
            request.approver(),
            "APPROVE_DLQ_REPLAY",
            "DLQ_REPLAY_REQUEST",
            String.valueOf(replayRequest.getId()),
            "{\"status\":\"PENDING\"}",
            "{\"status\":\"APPROVED\"}"
        );
        return replayRequest;
    }

    @Transactional
    public DlqReplayRequest reject(Long replayRequestId, RejectDlqReplayRequest request) {
        DlqReplayRequest replayRequest = findById(replayRequestId);
        if (replayRequest.getStatus() != DlqReplayStatus.PENDING) {
            throw new BusinessException(ErrorCode.RETRY_INVALID_STATUS, "Only PENDING replay request can be rejected");
        }
        replayRequest.reject(request.approver(), request.reason());

        auditLogService.record(
            request.approver(),
            "REJECT_DLQ_REPLAY",
            "DLQ_REPLAY_REQUEST",
            String.valueOf(replayRequest.getId()),
            "{\"status\":\"PENDING\"}",
            "{\"status\":\"REJECTED\"}"
        );
        return replayRequest;
    }

    @Transactional(noRollbackFor = BusinessException.class)
    public ExecutionHistory execute(Long replayRequestId, ExecuteDlqReplayRequest request) {
        DlqReplayRequest replayRequest = findById(replayRequestId);
        if (replayRequest.getStatus() != DlqReplayStatus.APPROVED) {
            throw new BusinessException(ErrorCode.DLQ_REPLAY_NOT_APPROVED);
        }

        DlqMessage message = replayRequest.getDlqMessage();

        try {
            validateReplayPolicy(message);

            Map<String, Object> payload = replayRequest.getPayloadOverrideJson() == null
                ? parsePayload(message.getPayload())
                : parsePayload(replayRequest.getPayloadOverrideJson());

            String idempotencyKey = "DLQ-REPLAY-%d-%s".formatted(message.getId(), UUID.randomUUID().toString().substring(0, 8));
            ExecutionHistory execution = executionOrchestrator.executeByTrigger(
                message.getInterfaceCode(),
                idempotencyKey,
                payload,
                TriggerType.RETRY,
                PolicyExecutionContext.system(null)
            );

            message.markReplayed();
            if (execution.getStatus() == ExecutionStatus.SUCCESS) {
                replayRequest.markExecuted();
            } else {
                replayRequest.markFailed();
            }

            auditLogService.record(
                request.executor(),
                "EXECUTE_DLQ_REPLAY",
                "DLQ_REPLAY_REQUEST",
                String.valueOf(replayRequest.getId()),
                "{\"status\":\"APPROVED\"}",
                "{\"status\":\"" + replayRequest.getStatus().name() + "\"}"
            );
            return execution;
        } catch (BusinessException exception) {
            if (exception.getErrorCode() == ErrorCode.DLQ_REPLAY_COOLDOWN) {
                throw exception;
            }
            if (exception.getErrorCode() == ErrorCode.DLQ_REPLAY_LIMIT_EXCEEDED) {
                message.markExhausted();
                replayRequest.markFailed();
                auditLogService.record(
                    request.executor(),
                    "EXECUTE_DLQ_REPLAY",
                    "DLQ_REPLAY_REQUEST",
                    String.valueOf(replayRequest.getId()),
                    "{\"status\":\"APPROVED\"}",
                    "{\"status\":\"FAILED\",\"errorCode\":\"DLQ_REPLAY_LIMIT_EXCEEDED\"}"
                );
                throw exception;
            }

            markReplayFailed(replayRequest, message, request.executor());
            throw exception;
        } catch (Exception exception) {
            markReplayFailed(replayRequest, message, request.executor());
            throw new BusinessException(ErrorCode.MQ_CONSUME_FAILED, "DLQ replay failed: " + exception.getMessage());
        }
    }

    @Transactional(readOnly = true)
    public DlqReplayRequest findById(Long replayRequestId) {
        return dlqReplayRequestRepository.findById(replayRequestId)
            .orElseThrow(() -> new BusinessException(ErrorCode.DLQ_REPLAY_REQUEST_NOT_FOUND));
    }

    @Transactional(readOnly = true)
    public Page<DlqReplayRequest> search(DlqReplayStatus status, LocalDate fromDate, LocalDate toDate, Pageable pageable) {
        LocalDateTime fromAt = fromDate == null ? null : fromDate.atStartOfDay();
        LocalDateTime toAtExclusive = toDate == null ? null : toDate.plusDays(1).atStartOfDay();
        return dlqReplayRequestRepository.search(status, fromAt, toAtExclusive, pageable);
    }

    private void validateReplayPolicy(DlqMessage message) {
        if (message.getStatus() == DlqMessageStatus.EXHAUSTED) {
            throw new BusinessException(ErrorCode.DLQ_REPLAY_LIMIT_EXCEEDED, "DLQ message is exhausted");
        }
        if (message.exceedsReplayLimit(policyProperties.getMaxAttempts())) {
            throw new BusinessException(ErrorCode.DLQ_REPLAY_LIMIT_EXCEEDED);
        }
        if (message.isInCooldown(policyProperties.getCooldownSeconds())) {
            throw new BusinessException(ErrorCode.DLQ_REPLAY_COOLDOWN);
        }
    }

    private void markReplayFailed(DlqReplayRequest replayRequest, DlqMessage message, String executor) {
        message.markReplayed();
        if (message.exceedsReplayLimit(policyProperties.getMaxAttempts())) {
            message.markExhausted();
        }
        replayRequest.markFailed();
        auditLogService.record(
            executor,
            "EXECUTE_DLQ_REPLAY",
            "DLQ_REPLAY_REQUEST",
            String.valueOf(replayRequest.getId()),
            "{\"status\":\"APPROVED\"}",
            "{\"status\":\"FAILED\"}"
        );
    }

    private Map<String, Object> parsePayload(String rawPayload) {
        try {
            return objectMapper.readValue(rawPayload, new TypeReference<>() {
            });
        } catch (JsonProcessingException exception) {
            throw new BusinessException(ErrorCode.INVALID_REQUEST, "Payload is not valid JSON object");
        }
    }

    private String toJson(Map<String, Object> payloadOverride) {
        if (payloadOverride == null) {
            return null;
        }
        try {
            return objectMapper.writeValueAsString(payloadOverride);
        } catch (JsonProcessingException exception) {
            throw new BusinessException(ErrorCode.INVALID_REQUEST, "payloadOverride must be valid JSON object");
        }
    }
}
