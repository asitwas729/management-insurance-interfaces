package com.example.interfacehub.application.mq;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

import com.example.interfacehub.application.audit.AuditLogService;
import com.example.interfacehub.application.execution.ExecutionOrchestrator;
import com.example.interfacehub.common.error.BusinessException;
import com.example.interfacehub.application.policy.PolicyExecutionContext;
import com.example.interfacehub.domain.execution.TriggerType;
import com.example.interfacehub.domain.mq.DlqReplayStatus;
import com.example.interfacehub.domain.mq.DlqMessage;
import com.example.interfacehub.domain.mq.DlqMessageStatus;
import com.example.interfacehub.domain.mq.DlqReplayRequest;
import com.example.interfacehub.infrastructure.persistence.DlqReplayRequestRepository;
import com.example.interfacehub.presentation.CreateDlqReplayRequest;
import com.example.interfacehub.presentation.ExecuteDlqReplayRequest;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Map;
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

        assertThat(request.getStatus()).isEqualTo(DlqReplayStatus.APPROVED);
    }

    @Test
    void execute_rejects_when_max_attempts_exceeded() {
        DlqReplayRequest request = approvedReplayRequestWithReplayCount(1);
        properties.setCooldownSeconds(0);
        properties.setMaxAttempts(1);
        when(dlqReplayRequestRepository.findById(1L)).thenReturn(Optional.of(request));

        assertThatThrownBy(() -> dlqReplayService.execute(1L, new ExecuteDlqReplayRequest("manager1")))
            .isInstanceOf(BusinessException.class);

        assertThat(request.getStatus()).isEqualTo(DlqReplayStatus.FAILED);
    }

    @Test
    void execute_marks_failed_and_increments_replay_count_when_orchestrator_throws() {
        DlqReplayRequest request = approvedReplayRequestWithReplayCount(0);
        properties.setCooldownSeconds(0);
        properties.setMaxAttempts(3);
        when(dlqReplayRequestRepository.findById(1L)).thenReturn(Optional.of(request));
        when(executionOrchestrator.executeByTrigger(
            anyString(),
            anyString(),
            anyMap(),
            any(TriggerType.class),
            any(PolicyExecutionContext.class)
        ))
            .thenThrow(new RuntimeException("boom"));

        assertThatThrownBy(() -> dlqReplayService.execute(1L, new ExecuteDlqReplayRequest("manager1")))
            .isInstanceOf(BusinessException.class);

        assertThat(request.getStatus()).isEqualTo(DlqReplayStatus.FAILED);
        assertThat(request.getDlqMessage().getReplayCount()).isEqualTo(1);
    }

    @Test
    void requestReplay_rejects_when_dlq_message_is_exhausted() {
        DlqMessage message = DlqMessage.create("MQ_IF", "topic.policy.report", "{}", "failed");
        message.markExhausted();
        when(dlqMessageService.findById(1L)).thenReturn(message);

        assertThatThrownBy(() -> dlqReplayService.requestReplay(
            1L,
            new CreateDlqReplayRequest("operator1", "IF-MQ-001", "retry", (Map<String, Object>) null)
        )).isInstanceOf(BusinessException.class);
    }

    @Test
    void requestReplay_rejects_and_marks_exhausted_when_max_attempts_already_reached() {
        DlqMessage message = DlqMessage.create("MQ_IF", "topic.policy.report", "{}", "failed");
        for (int i = 0; i < properties.getMaxAttempts(); i++) {
            message.markReplayed();
        }
        when(dlqMessageService.findById(1L)).thenReturn(message);

        assertThatThrownBy(() -> dlqReplayService.requestReplay(
            1L,
            new CreateDlqReplayRequest("operator1", "IF-MQ-001", "retry", (Map<String, Object>) null)
        )).isInstanceOf(BusinessException.class);

        assertThat(message.getStatus()).isEqualTo(DlqMessageStatus.EXHAUSTED);
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
