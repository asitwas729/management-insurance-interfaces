package com.example.interfacehub.application.execution;

import com.example.interfacehub.application.notification.NotificationService;
import com.example.interfacehub.application.policy.PolicyEnforcementService;
import com.example.interfacehub.application.policy.PolicyExecutionContext;
import com.example.interfacehub.application.policy.ResolvedPolicy;
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
import io.opentelemetry.api.GlobalOpenTelemetry;
import io.opentelemetry.api.trace.Tracer;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.time.LocalDateTime;
import org.springframework.http.HttpHeaders;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.util.StringUtils;

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
    private final NotificationService notificationService;
    private final PolicyEnforcementService policyEnforcementService;
    private final Tracer tracer; // Inject Tracer

    @Autowired
    public ExecutionOrchestrator(
        InterfaceRegistryService interfaceRegistryService,
        ExecutorRouter executorRouter,
        ExecutionPersistenceService executionPersistenceService,
        ObjectMapper objectMapper,
        SensitiveDataMasker sensitiveDataMasker,
        MeterRegistry meterRegistry,
        StandardContractService standardContractService,
        NotificationService notificationService,
        PolicyEnforcementService policyEnforcementService,
        Tracer tracer // Add Tracer to constructor
    ) {
        this.interfaceRegistryService = interfaceRegistryService;
        this.executorRouter = executorRouter;
        this.executionPersistenceService = executionPersistenceService;
        this.objectMapper = objectMapper;
        this.sensitiveDataMasker = sensitiveDataMasker;
        this.meterRegistry = meterRegistry;
        this.standardContractService = standardContractService;
        this.notificationService = notificationService;
        this.policyEnforcementService = policyEnforcementService;
        this.tracer = tracer; // Assign Tracer
    }

    public ExecutionOrchestrator(
        InterfaceRegistryService interfaceRegistryService,
        ExecutorRouter executorRouter,
        ExecutionPersistenceService executionPersistenceService,
        ObjectMapper objectMapper,
        SensitiveDataMasker sensitiveDataMasker,
        MeterRegistry meterRegistry,
        StandardContractService standardContractService,
        NotificationService notificationService,
        PolicyEnforcementService policyEnforcementService
    ) {
        this(
            interfaceRegistryService,
            executorRouter,
            executionPersistenceService,
            objectMapper,
            sensitiveDataMasker,
            meterRegistry,
            standardContractService,
            notificationService,
            policyEnforcementService,
            GlobalOpenTelemetry.getTracer("com.example.interfacehub.execution")
        );
    }

    public ExecutionHistory executeManually(
        String interfaceCode,
        ExecuteInterfaceRequest request,
        PolicyExecutionContext policyExecutionContext
    ) {
        return executeByTrigger(
            interfaceCode,
            request.idempotencyKey(),
            request.payload(),
            TriggerType.MANUAL,
            policyExecutionContext
        );
    }

    public ExecutionHistory executeRetry(String interfaceCode, String idempotencyKey, Map<String, Object> payload) {
        InterfaceDefinition definition = interfaceRegistryService.findByCode(interfaceCode);
        return executeByTrigger(
            interfaceCode,
            idempotencyKey,
            payload,
            TriggerType.RETRY,
            PolicyExecutionContext.system(definition.getExternalOrg())
        );
    }

    public ExecutionHistory executeByTrigger(
        String interfaceCode,
        String idempotencyKey,
        Map<String, Object> payload,
        TriggerType triggerType,
        PolicyExecutionContext policyExecutionContext
    ) {
        InterfaceDefinition definition = interfaceRegistryService.findByCode(interfaceCode);
        InterfaceConfigVersion config = interfaceRegistryService.findPublishedConfig(definition);
        String executionId = UUID.randomUUID().toString();
        PolicyExecutionContext effectivePolicyContext = ensurePolicyContext(policyExecutionContext, definition);
        ResolvedPolicy policy = policyEnforcementService.resolveAndSnapshot(
            executionId,
            definition,
            config.getTimeoutMillis(),
            effectivePolicyContext
        );

        executionPersistenceService.reserveIdempotencyKey(idempotencyKey, interfaceCode, executionId);
        String requestPayloadRaw = toJson(payload);
        String requestPayloadForLog = policy.maskRequestPayload()
            ? sensitiveDataMasker.mask(requestPayloadRaw)
            : requestPayloadRaw;

        TriggerType effectiveTriggerType = config.isSandboxMode() ? TriggerType.SANDBOX : triggerType;

        ExecutionHistory runningHistory = executionPersistenceService.createRunningHistory(
            executionId,
            definition.getInterfaceCode(),
            definition.getProtocolType(),
            effectiveTriggerType,
            requestPayloadForLog
        );

        boolean inMaintenanceWindow = standardContractService.isMaintenanceWindowActive(
            definition.getExternalOrg(),
            LocalDateTime.now()
        );
        if (inMaintenanceWindow) {
            ExecutionHistory suppressed = executionPersistenceService.markFailed(
                runningHistory.getId(),
                ErrorCode.EXT_MAINTENANCE.name(),
                maskForResponsePolicy(
                    "Execution suppressed due to maintenance window of external org: " + definition.getExternalOrg(),
                    policy
                ),
                0L
            );
            recordMetrics(interfaceCode, false, 0L, true);
            return suppressed;
        }

        if (config.isSandboxMode()) {
            ExecutionHistory finished = executionPersistenceService.markSuccess(
                runningHistory.getId(),
                maskForResponsePolicy(config.getMockResponseBody(), policy),
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
            policy.timeoutMillis()
        );

        try {
            ExecutionResult result = executeWithRetry(context, policy);
            if (result.success()) {
                ExecutionHistory finished = executionPersistenceService.markSuccess(
                    runningHistory.getId(),
                    maskForResponsePolicy(result.responsePayload(), policy),
                    result.latencyMillis()
                );
                recordMetrics(interfaceCode, true, result.latencyMillis(), false);
                checkSla(definition, result.latencyMillis());
                return finished;
            }
            ExecutionHistory failed = executionPersistenceService.markFailed(
                runningHistory.getId(),
                result.errorCode(),
                maskForResponsePolicy(result.errorMessage(), policy),
                result.latencyMillis()
            );
            recordMetrics(interfaceCode, false, result.latencyMillis(), false);
            return failed;
        } catch (BusinessException exception) {
            executionPersistenceService.markFailed(
                runningHistory.getId(),
                exception.getErrorCode().name(),
                maskForResponsePolicy(exception.getMessage(), policy),
                0L
            );
            recordMetrics(interfaceCode, false, 0L, false);
            throw exception;
        } catch (RuntimeException exception) {
            executionPersistenceService.markFailed(
                runningHistory.getId(),
                ErrorCode.INTERNAL_ERROR.name(),
                maskForResponsePolicy(exception.getMessage(), policy),
                0L
            );
            recordMetrics(interfaceCode, false, 0L, false);
            throw exception;
        }
    }

    private ExecutionResult executeWithRetry(ExecutionContext context, ResolvedPolicy policy) {
        ExecutionResult lastResult = null;
        for (int attempt = 0; attempt <= policy.retryMaxAttempts(); attempt++) {
            ExecutionResult result = executorRouter.routeAndExecute(context);
            lastResult = result;
            if (result.success()) {
                return result;
            }
            if (!isRetryableError(result.errorCode()) || attempt == policy.retryMaxAttempts()) {
                return result;
            }
            if (policy.retryIntervalMillis() > 0) {
                try {
                    Thread.sleep(policy.retryIntervalMillis());
                } catch (InterruptedException interruptedException) {
                    Thread.currentThread().interrupt();
                    return result;
                }
            }
        }
        return lastResult == null
            ? ExecutionResult.failure(ErrorCode.INTERNAL_ERROR.name(), "Execution failed without result", 0L)
            : lastResult;
    }

    private boolean isRetryableError(String errorCode) {
        return ErrorCode.TIMEOUT.name().equals(errorCode)
            || ErrorCode.REST_CALL_FAILED.name().equals(errorCode)
            || ErrorCode.SOAP_CALL_FAILED.name().equals(errorCode)
            || ErrorCode.SFTP_TRANSFER_FAILED.name().equals(errorCode)
            || ErrorCode.BATCH_FAILED.name().equals(errorCode)
            || ErrorCode.MQ_CONSUME_FAILED.name().equals(errorCode)
            || ErrorCode.EXT_5XX.name().equals(errorCode)
            || ErrorCode.CIRCUIT_OPEN.name().equals(errorCode)
            || ErrorCode.RATE_LIMITED.name().equals(errorCode)
            || ErrorCode.BULKHEAD_FULL.name().equals(errorCode);
    }

    private String maskForResponsePolicy(String raw, ResolvedPolicy policy) {
        if (!policy.maskResponsePayload()) {
            return raw;
        }
        return sensitiveDataMasker.mask(raw);
    }

    private PolicyExecutionContext ensurePolicyContext(
        PolicyExecutionContext context,
        InterfaceDefinition definition
    ) {
        if (context == null) {
            return PolicyExecutionContext.system(definition.getExternalOrg());
        }
        String partnerId = StringUtils.hasText(context.partnerId()) ? context.partnerId() : definition.getExternalOrg();
        return new PolicyExecutionContext(
            context.clientId(),
            context.clientSecret(),
            context.apiKey(),
            context.authorizationHeader(),
            partnerId,
            context.remoteIp(),
            context.roles()
        );
    }

    private void checkSla(InterfaceDefinition definition, long latencyMillis) {
        Long slaMillis = definition.getSlaMillis();
        if (slaMillis == null || latencyMillis <= slaMillis) {
            return;
        }
        Counter.builder("execution.sla_breach")
            .tag("interfaceCode", definition.getInterfaceCode())
            .register(meterRegistry)
            .increment();
        log.warn("[SLA] Breach — interfaceCode={}, latency={}ms, sla={}ms", definition.getInterfaceCode(), latencyMillis, slaMillis);
        notificationService.sendSlaBreachAlert(definition.getInterfaceCode(), latencyMillis, slaMillis);
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
