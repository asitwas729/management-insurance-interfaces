package com.example.interfacehub.application.retry;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

import com.example.interfacehub.application.audit.AuditLogService;
import com.example.interfacehub.application.execution.ExecutionOrchestrator;
import com.example.interfacehub.application.registry.InterfaceRegistryService;
import com.example.interfacehub.common.error.BusinessException;
import com.example.interfacehub.domain.interfaceconfig.InterfaceDefinition;
import com.example.interfacehub.domain.interfaceconfig.ProtocolType;
import com.example.interfacehub.domain.retry.RetryStatus;
import com.example.interfacehub.domain.retry.RetryTask;
import com.example.interfacehub.infrastructure.persistence.ExecutionHistoryRepository;
import com.example.interfacehub.infrastructure.persistence.RetryTaskRepository;
import com.example.interfacehub.presentation.ApproveRetryTaskRequest;
import com.example.interfacehub.presentation.RejectRetryTaskRequest;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.transaction.PlatformTransactionManager;

@ExtendWith(MockitoExtension.class)
class RetryTaskServiceTest {

    @Mock
    private RetryTaskRepository retryTaskRepository;

    @Mock
    private ExecutionHistoryRepository executionHistoryRepository;

    @Mock
    private InterfaceRegistryService interfaceRegistryService;

    @Mock
    private ExecutionOrchestrator executionOrchestrator;

    @Mock
    private AuditLogService auditLogService;

    @Mock
    private PlatformTransactionManager transactionManager;

    private RetryTaskService retryTaskService;
    private RetryTask retryTask;

    @BeforeEach
    void setUp() {
        retryTaskService = new RetryTaskService(
            retryTaskRepository,
            executionHistoryRepository,
            interfaceRegistryService,
            executionOrchestrator,
            auditLogService,
            new ObjectMapper(),
            transactionManager
        );
        InterfaceDefinition definition = InterfaceDefinition.create(
            "RETRY_IF",
            "Retry interface",
            ProtocolType.REST,
            "PolicyCore",
            "FSS",
            3000L
        );
        retryTask = RetryTask.request(definition, "EXEC-1", "operator1", "IF-EXT-001", "temporary failure");
    }

    @Test
    void approve_moves_pending_to_approved() {
        when(retryTaskRepository.findById(1L)).thenReturn(Optional.of(retryTask));

        RetryTask approved = retryTaskService.approveRetry(1L, new ApproveRetryTaskRequest("manager1"));

        assertThat(approved.getStatus()).isEqualTo(RetryStatus.APPROVED);
        assertThat(approved.getApprover()).isEqualTo("manager1");
    }

    @Test
    void reject_after_approved_is_rejected_as_invalid_transition() {
        retryTask.approve("manager1");
        when(retryTaskRepository.findById(1L)).thenReturn(Optional.of(retryTask));

        assertThatThrownBy(() -> retryTaskService.rejectRetry(1L, new RejectRetryTaskRequest("manager1", "invalid")))
            .isInstanceOf(BusinessException.class)
            .hasMessageContaining("Only PENDING retry task can be rejected");
    }

    @Test
    void execute_without_approval_is_rejected() {
        when(retryTaskRepository.findById(1L)).thenReturn(Optional.of(retryTask));

        assertThatThrownBy(() -> retryTaskService.executeApprovedRetry(1L))
            .isInstanceOf(BusinessException.class);
    }
}
