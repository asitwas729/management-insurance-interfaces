package com.example.interfacehub.application.execution;

import com.example.interfacehub.application.registry.InterfaceRegistryService;
import com.example.interfacehub.application.standard.StandardContractService;
import com.example.interfacehub.common.error.BusinessException;
import com.example.interfacehub.common.error.ErrorCode;
import com.example.interfacehub.common.security.SensitiveDataMasker;
import com.example.interfacehub.domain.execution.ExecutionContext;
import com.example.interfacehub.domain.execution.ExecutionHistory;
import com.example.interfacehub.domain.execution.ExecutionResult;
import com.example.interfacehub.domain.execution.TriggerType;
import com.example.interfacehub.domain.interfaceconfig.InterfaceConfigVersion;
import com.example.interfacehub.domain.interfaceconfig.InterfaceDefinition;
import com.example.interfacehub.presentation.ExecuteInterfaceRequest;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.time.LocalDateTime;
import org.springframework.http.HttpHeaders;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;

@Service
public class ExecutionOrchestrator {

    private static final Logger log = LoggerFactory.getLogger(ExecutionOrchestrator.class);

    private final InterfaceRegistryService interfaceRegistryService;
    private final ExecutorRouter executorRouter;
    private final ExecutionPersistenceService executionPersistenceService;
    private final ObjectMapper objectMapper;
    private final SensitiveDataMasker sensitiveDataMasker;
    private final MeterRegistry meterRegistry;
    private final StandardContractService standardContractService;

    public ExecutionOrchestrator(
        InterfaceRegistryService interfaceRegistryService,
        ExecutorRouter executorRouter,
        ExecutionPersistenceService executionPersistenceService,
        ObjectMapper objectMapper,
        SensitiveDataMasker sensitiveDataMasker,
        MeterRegistry meterRegistry,
        StandardContractService standardContractService
    ) {
        this.interfaceRegistryService = interfaceRegistryService;
        this.executorRouter = executorRouter;
        this.executionPersistenceService = executionPersistenceService;
        this.objectMapper = objectMapper;
        this.sensitiveDataMasker = sensitiveDataMasker;
        this.meterRegistry = meterRegistry;
        this.standardContractService = standardContractService;
    }

    public ExecutionHistory executeManually(String interfaceCode, ExecuteInterfaceRequest request) {
        return executeByTrigger(interfaceCode, request.idempotencyKey(), request.payload(), TriggerType.MANUAL);
    }

    public ExecutionHistory executeRetry(String interfaceCode, String idempotencyKey, Map<String, Object> payload) {
        return executeByTrigger(interfaceCode, idempotencyKey, payload, TriggerType.RETRY);
    }

    public ExecutionHistory executeByTrigger(
        String interfaceCode,
        String idempotencyKey,
        Map<String, Object> payload,
        TriggerType triggerType
    ) {
        InterfaceDefinition definition = interfaceRegistryService.findByCode(interfaceCode);
        InterfaceConfigVersion config = interfaceRegistryService.findPublishedConfig(definition);
        String executionId = UUID.randomUUID().toString();
        executionPersistenceService.reserveIdempotencyKey(idempotencyKey, interfaceCode, executionId);
        String requestPayloadRaw = toJson(payload);
        String requestPayloadMasked = sensitiveDataMasker.mask(requestPayloadRaw);

        TriggerType effectiveTriggerType = config.isSandboxMode() ? TriggerType.SANDBOX : triggerType;

        ExecutionHistory runningHistory = executionPersistenceService.createRunningHistory(
            executionId,
            definition.getInterfaceCode(),
            definition.getProtocolType(),
            effectiveTriggerType,
            requestPayloadMasked
        );

        boolean inMaintenanceWindow = standardContractService.isMaintenanceWindowActive(
            definition.getExternalOrg(),
            LocalDateTime.now()
        );
        if (inMaintenanceWindow) {
            ExecutionHistory suppressed = executionPersistenceService.markFailed(
                runningHistory.getId(),
                ErrorCode.EXT_MAINTENANCE.name(),
                "Execution suppressed due to maintenance window of external org: " + definition.getExternalOrg(),
                0L
            );
            recordMetrics(interfaceCode, false, 0L, true);
            return suppressed;
        }

        if (config.isSandboxMode()) {
            ExecutionHistory finished = executionPersistenceService.markSuccess(
                runningHistory.getId(),
                config.getMockResponseBody(),
                0L
            );
            recordMetrics(interfaceCode, true, 0L, false);
            return finished;
        }

        ExecutionContext context = new ExecutionContext(
            definition.getInterfaceCode(),
            definition.getProtocolType(),
            config.getEndpoint(),
            toHeaders(config.getHeadersJson(), interfaceCode),
            requestPayloadRaw,
            config.getTimeoutMillis()
        );

        try {
            ExecutionResult result = executorRouter.routeAndExecute(context);
            if (result.success()) {
                ExecutionHistory finished = executionPersistenceService.markSuccess(
                    runningHistory.getId(),
                    sensitiveDataMasker.mask(result.responsePayload()),
                    result.latencyMillis()
                );
                recordMetrics(interfaceCode, true, result.latencyMillis(), false);
                return finished;
            }
            ExecutionHistory failed = executionPersistenceService.markFailed(
                runningHistory.getId(),
                result.errorCode(),
                sensitiveDataMasker.mask(result.errorMessage()),
                result.latencyMillis()
            );
            recordMetrics(interfaceCode, false, result.latencyMillis(), false);
            return failed;
        } catch (BusinessException exception) {
            executionPersistenceService.markFailed(
                runningHistory.getId(),
                exception.getErrorCode().name(),
                sensitiveDataMasker.mask(exception.getMessage()),
                0L
            );
            recordMetrics(interfaceCode, false, 0L, false);
            throw exception;
        } catch (RuntimeException exception) {
            executionPersistenceService.markFailed(
                runningHistory.getId(),
                ErrorCode.INTERNAL_ERROR.name(),
                sensitiveDataMasker.mask(exception.getMessage()),
                0L
            );
            recordMetrics(interfaceCode, false, 0L, false);
            throw exception;
        }
    }

    private void recordMetrics(String interfaceCode, boolean success, long latencyMillis, boolean suppressed) {
        String outcome = suppressed ? "suppressed" : (success ? "success" : "failure");
        Counter.builder("execution.count")
            .tag("interfaceCode", interfaceCode)
            .tag("outcome", outcome)
            .register(meterRegistry)
            .increment();
        Timer.builder("execution.latency")
            .tag("interfaceCode", interfaceCode)
            .tag("outcome", outcome)
            .register(meterRegistry)
            .record(latencyMillis, TimeUnit.MILLISECONDS);
    }

    private MultiValueMap<String, String> toHeaders(String headersJson, String interfaceCode) {
        MultiValueMap<String, String> headers = new LinkedMultiValueMap<>();
        headers.add(HttpHeaders.CONTENT_TYPE, "application/json");

        if (headersJson == null || headersJson.isBlank()) {
            return headers;
        }

        try {
            Map<String, String> parsed = objectMapper.readValue(headersJson, new TypeReference<>() {
            });
            parsed.forEach(headers::add);
            return headers;
        } catch (JsonProcessingException exception) {
            log.error("Invalid headersJson for interfaceCode={}", interfaceCode, exception);
            throw new BusinessException(ErrorCode.INVALID_CONFIG, "Published config headersJson is invalid");
        }
    }

    private String toJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException exception) {
            return "{}";
        }
    }
}
