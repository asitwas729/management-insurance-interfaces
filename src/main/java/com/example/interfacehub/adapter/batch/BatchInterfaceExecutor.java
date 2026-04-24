package com.example.interfacehub.adapter.batch;

import com.example.interfacehub.application.execution.InterfaceExecutor;
import com.example.interfacehub.common.error.ErrorCode;
import com.example.interfacehub.domain.execution.ExecutionContext;
import com.example.interfacehub.domain.execution.ExecutionResult;
import com.example.interfacehub.domain.interfaceconfig.ProtocolType;
import com.example.interfacehub.infrastructure.batch.InterfaceHubBatchJobConfig;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.Tracer;
import io.opentelemetry.context.Scope;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.springframework.batch.core.BatchStatus;
import org.springframework.batch.core.Job;
import org.springframework.batch.core.JobExecution;
import org.springframework.batch.core.JobParameter;
import org.springframework.batch.core.JobParameters;
import org.springframework.batch.core.JobParametersBuilder;
import org.springframework.batch.core.StepExecution;
import org.springframework.batch.core.launch.JobLauncher;
import org.springframework.stereotype.Component;
import com.example.interfacehub.infrastructure.resilience.ExternalCallResilienceService;

@Component
public class BatchInterfaceExecutor implements InterfaceExecutor {

    private final JobLauncher jobLauncher;
    private final Map<String, Job> jobsByName;
    private final ObjectMapper objectMapper;
    private final ExternalCallResilienceService resilienceService;
    private final MeterRegistry meterRegistry; // Add MeterRegistry
    private final Tracer tracer; // Add Tracer

    public BatchInterfaceExecutor(
        JobLauncher jobLauncher,
        List<Job> jobs,
        ObjectMapper objectMapper,
        ExternalCallResilienceService resilienceService,
        MeterRegistry meterRegistry, // Inject MeterRegistry
        Tracer tracer // Inject Tracer
    ) {
        this.jobLauncher = jobLauncher;
        this.jobsByName = jobs.stream().collect(Collectors.toMap(Job::getName, job -> job, (left, right) -> left));
        this.objectMapper = objectMapper;
        this.resilienceService = resilienceService;
        this.meterRegistry = meterRegistry;
        this.tracer = tracer; // Assign Tracer
    }

    @Override
    public ProtocolType supportType() {
        return ProtocolType.BATCH;
    }

    @Override
    public ExecutionResult execute(ExecutionContext context) {
        Span span = tracer.spanBuilder("BatchInterfaceExecutor.execute").startSpan();
        ExecutionResult result;
        try (Scope scope = span.makeCurrent()) {
            Timer timer = Timer.builder("execution.batch.latency")
                .tag("interfaceCode", context.interfaceCode())
                .tag("outcome", "unknown") // Will be updated after execution
                .register(meterRegistry);

            result = executeBatchJob(context);

            // Update span and metrics based on the result
            String outcome = result.success() ? "success" : "failure";
            timer.record(Duration.ofMillis(result.latencyMillis()));
            meterRegistry.counter("execution.batch.count", "interfaceCode", context.interfaceCode(), "outcome", outcome).increment();

            if (result.success()) {
                span.setStatus(io.opentelemetry.api.trace.StatusCode.OK);
                span.setAttribute("execution.outcome", "success");
                span.setAttribute("execution.latency", result.latencyMillis());
            } else {
                span.setStatus(io.opentelemetry.api.trace.StatusCode.ERROR);
                span.setAttribute("execution.outcome", "failure");
                span.setAttribute("execution.error.code", result.errorCode());
                span.setAttribute("execution.latency", result.latencyMillis());
            }
            span.setAttribute("execution.interfaceCode", context.interfaceCode());

        } catch (Throwable t) { // Catching Throwable to include potential RuntimeExceptions from resilienceService
            // Handle exceptions from resilienceService or the lambda itself
            span.setStatus(io.opentelemetry.api.trace.StatusCode.ERROR);
            span.setAttribute("execution.outcome", "failure");
            span.setAttribute("execution.error.code", ErrorCode.BATCH_FAILED.name()); // Default error code for Batch
            span.recordException(t);
            // Re-throw to ensure the orchestrator gets the exception
            if (t instanceof RuntimeException runtimeException) {
                throw runtimeException;
            }
            throw new RuntimeException(t);
        } finally {
            span.end();
        }
        return result;
    }

    private ExecutionResult executeBatchJob(ExecutionContext context) {
        return resilienceService.execute(context.interfaceCode(), () -> {
            long start = System.currentTimeMillis(); // Use a new start time for the actual operation
            Map<String, Object> payload = parsePayload(context.payload());
            String jobName = resolveJobName(context, payload);
            Job job = jobsByName.get(jobName);
            if (job == null) {
                return ExecutionResult.failure(
                    ErrorCode.INVALID_CONFIG.name(),
                    "Batch job not found: " + jobName,
                    elapsed(start)
                );
            }

            JobParameters params = toJobParameters(context, payload);
            JobExecution execution;
            try {
                execution = jobLauncher.run(job, params);
            } catch (Exception exception) {
                return ExecutionResult.failure(ErrorCode.BATCH_FAILED.name(), exception.getMessage(), elapsed(start));
            }

            ExecutionResult executionResult;
            if (execution.getStatus() == BatchStatus.COMPLETED) {
                String resultPayload = extractResultPayload(execution);
                executionResult = ExecutionResult.success(resultPayload, elapsed(start));
            } else {
                String reason = execution.getAllFailureExceptions().stream()
                    .map(Throwable::getMessage)
                    .filter(message -> message != null && !message.isBlank())
                    .findFirst()
                    .orElse("Batch execution status: " + execution.getStatus().name());
                executionResult = ExecutionResult.failure(ErrorCode.BATCH_FAILED.name(), reason, elapsed(start));
            }
            return executionResult;
        }, ErrorCode.BATCH_FAILED);
    }

    private String resolveJobName(ExecutionContext context, Map<String, Object> payload) {
        Object payloadJobName = payload.get("jobName");
        if (payloadJobName != null && !payloadJobName.toString().isBlank()) {
            return payloadJobName.toString().trim();
        }
        if (context.endpoint() != null && !context.endpoint().isBlank()) {
            return context.endpoint().trim();
        }
        return InterfaceHubBatchJobConfig.JOB_NAME;
    }

    private JobParameters toJobParameters(ExecutionContext context, Map<String, Object> payload) {
        boolean simulateFailure = Boolean.TRUE.equals(payload.get("simulateFailure"));
        long itemCount = asLong(payload.getOrDefault("itemCount", 1));
        String requestedBy = payload.getOrDefault("requestedBy", "system").toString();

        return new JobParametersBuilder()
            .addJobParameter("requestedAt", new JobParameter<>(System.currentTimeMillis(), Long.class))
            .addJobParameter("interfaceCode", new JobParameter<>(context.interfaceCode(), String.class))
            .addJobParameter("simulateFailure", new JobParameter<>(Boolean.toString(simulateFailure), String.class))
            .addJobParameter("itemCount", new JobParameter<>(itemCount, Long.class))
            .addJobParameter("requestedBy", new JobParameter<>(requestedBy, String.class))
            .toJobParameters();
    }

    private String extractResultPayload(JobExecution execution) {
        for (StepExecution stepExecution : execution.getStepExecutions()) {
            String resultPayload = stepExecution.getExecutionContext().getString("resultPayload", null);
            if (resultPayload != null) {
                return resultPayload;
            }
        }
        try {
            return objectMapper.writeValueAsString(Map.of(
                "jobExecutionId", execution.getId(),
                "status", execution.getStatus().name()
            ));
        } catch (Exception exception) {
            return "{\"jobExecutionId\":\"%s\",\"status\":\"%s\"}".formatted(execution.getId(), execution.getStatus().name());
        }
    }

    private Map<String, Object> parsePayload(String payload) {
        if (payload == null || payload.isBlank()) {
            return Map.of();
        }
        try {
            return objectMapper.readValue(payload, new TypeReference<>() {
            });
        } catch (Exception exception) {
            return Map.of();
        }
    }

    private long asLong(Object value) {
        if (value instanceof Number number) {
            return number.longValue();
        }
        try {
            return Long.parseLong(String.valueOf(value));
        } catch (Exception exception) {
            return 1L;
        }
    }

    private long elapsed(long start) {
        return System.currentTimeMillis() - start;
    }
}
