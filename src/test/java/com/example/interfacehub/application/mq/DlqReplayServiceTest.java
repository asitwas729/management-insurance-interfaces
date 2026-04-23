package com.example.interfacehub.application.mq;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

import com.example.interfacehub.application.audit.AuditLogService;
import com.example.interfacehub.application.execution.ExecutionOrchestrator;
import com.example.interfacehub.common.error.BusinessException;
import com.example.interfacehub.domain.mq.DlqMessage;
import com.example.interfacehub.domain.mq.DlqReplayRequest;
import com.example.interfacehub.infrastructure.persistence.DlqReplayRequestRepository;
import com.example.interfacehub.presentation.ExecuteDlqReplayRequest;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class DlqReplayServiceTest {

    @Mock
    private DlqMessageService dlqMessageService;

    @Mock
    private DlqReplayRequestRepository dlqReplayRequestRepository;

    @Mock
    private ExecutionOrchestrator executionOrchestrator;

    @Mock
    private AuditLogService auditLogService;

    private DlqReplayPolicyProperties properties;
    private DlqReplayService dlqReplayService;

    @BeforeEach
    void setUp() {
        properties = new DlqReplayPolicyProperties();
        dlqReplayService = new DlqReplayService(
            dlqMessageService,
            dlqReplayRequestRepository,
            new ObjectMapper(),
            executionOrchestrator,
            properties,
            auditLogService
        );
    }

    @Test
    void execute_rejects_when_cooldown_has_not_elapsed() {
        DlqReplayRequest request = approvedReplayRequestWithReplayCount(1);
        properties.setCooldownSeconds(60);
        properties.setMaxAttempts(3);
        when(dlqReplayRequestRepository.findById(1L)).thenReturn(Optional.of(request));

        assertThatThrownBy(() -> dlqReplayService.execute(1L, new ExecuteDlqReplayRequest("manager1")))
            .isInstanceOf(BusinessException.class);
    }

    @Test
    void execute_rejects_when_max_attempts_exceeded() {
        DlqReplayRequest request = approvedReplayRequestWithReplayCount(1);
        properties.setCooldownSeconds(0);
        properties.setMaxAttempts(1);
        when(dlqReplayRequestRepository.findById(1L)).thenReturn(Optional.of(request));

        assertThatThrownBy(() -> dlqReplayService.execute(1L, new ExecuteDlqReplayRequest("manager1")))
            .isInstanceOf(BusinessException.class);
    }

    private DlqReplayRequest approvedReplayRequestWithReplayCount(int replayCount) {
        DlqMessage message = DlqMessage.create("MQ_IF", "topic.policy.report", "{\"simulateFailure\":false}", "failed");
        for (int i = 0; i < replayCount; i++) {
            message.markReplayed();
        }
        DlqReplayRequest request = DlqReplayRequest.request(message, "operator1", "IF-MQ-001", "retry", null);
        request.approve("manager1");
        return request;
    }
}
