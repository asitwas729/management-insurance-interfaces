package com.example.interfacehub.application.execution;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.example.interfacehub.application.notification.NotificationService;
import com.example.interfacehub.application.policy.PolicyEnforcementService;
import com.example.interfacehub.application.policy.PolicyExecutionContext;
import com.example.interfacehub.application.policy.ResolvedPolicy;
import com.example.interfacehub.application.registry.InterfaceRegistryService;
import com.example.interfacehub.application.standard.StandardContractService;
import com.example.interfacehub.common.security.SensitiveDataMasker;
import com.example.interfacehub.domain.execution.ExecutionHistory;
import com.example.interfacehub.domain.execution.ExecutionResult;
import com.example.interfacehub.domain.execution.TriggerType;
import com.example.interfacehub.domain.interfaceconfig.InterfaceConfigVersion;
import com.example.interfacehub.domain.interfaceconfig.InterfaceDefinition;
import com.example.interfacehub.domain.interfaceconfig.ProtocolType;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class SlaMonitoringTest {

    @Mock
    private InterfaceRegistryService interfaceRegistryService;

    @Mock
    private ExecutorRouter executorRouter;

    @Mock
    private ExecutionPersistenceService executionPersistenceService;

    @Mock
    private StandardContractService standardContractService;

    @Mock
    private NotificationService notificationService;

    @Mock
    private PolicyEnforcementService policyEnforcementService;

    private SimpleMeterRegistry meterRegistry;
    private ExecutionOrchestrator orchestrator;

    @BeforeEach
    void setUp() {
        meterRegistry = new SimpleMeterRegistry();
        orchestrator = new ExecutionOrchestrator(
            interfaceRegistryService,
            executorRouter,
            executionPersistenceService,
            new ObjectMapper(),
            new SensitiveDataMasker(new ObjectMapper()),
            meterRegistry,
            standardContractService,
            notificationService,
            policyEnforcementService
        );
    }

    @Test
    void latency_over_sla_records_breach_metric() {
        InterfaceDefinition definition = InterfaceDefinition.create(
            "SLA_IF",
            "SLA interface",
            ProtocolType.REST,
            "PolicyCore",
            "FSS",
            50L
        );
        InterfaceConfigVersion config = InterfaceConfigVersion.create(
            definition,
            1,
            "https://example.test/report",
            "NONE",
            "{}",
            3000L
        );
        config.publish();
        ExecutionHistory running = ExecutionHistory.start("EXEC-1", "SLA_IF", ProtocolType.REST, TriggerType.MANUAL, "{}");
        ExecutionHistory finished = ExecutionHistory.start("EXEC-1", "SLA_IF", ProtocolType.REST, TriggerType.MANUAL, "{}");
        finished.markSuccess("{\"ok\":true}", 120L);

        when(interfaceRegistryService.findByCode("SLA_IF")).thenReturn(definition);
        when(interfaceRegistryService.findPublishedConfig(definition)).thenReturn(config);
        when(standardContractService.isMaintenanceWindowActive(eq("FSS"), any())).thenReturn(false);
        when(executionPersistenceService.createRunningHistory(any(), eq("SLA_IF"), eq(ProtocolType.REST), eq(TriggerType.MANUAL), any()))
            .thenReturn(running);
        when(executionPersistenceService.markSuccess(any(), any(), anyLong())).thenReturn(finished);
        when(executorRouter.routeAndExecute(any())).thenReturn(ExecutionResult.success("{\"ok\":true}", 120L));
        when(policyEnforcementService.resolveAndSnapshot(any(), eq(definition), eq(3000L), any()))
            .thenReturn(new ResolvedPolicy("DEFAULT", 3000L, 0, 0L, true, true));

        orchestrator.executeByTrigger(
            "SLA_IF",
            "IDEMP-1",
            Map.<String, Object>of("policyNo", "P1"),
            TriggerType.MANUAL,
            PolicyExecutionContext.system("FSS")
        );

        assertThat(meterRegistry.counter("execution.sla_breach", "interfaceCode", "SLA_IF").count()).isEqualTo(1.0);
        verify(notificationService).sendSlaBreachAlert("SLA_IF", 120L, 50L);
    }
}
