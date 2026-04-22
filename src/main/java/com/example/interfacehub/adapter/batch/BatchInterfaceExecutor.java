package com.example.interfacehub.adapter.batch;

import com.example.interfacehub.application.execution.InterfaceExecutor;
import com.example.interfacehub.common.error.ErrorCode;
import com.example.interfacehub.domain.execution.ExecutionContext;
import com.example.interfacehub.domain.execution.ExecutionResult;
import com.example.interfacehub.domain.interfaceconfig.ProtocolType;
import com.example.interfacehub.infrastructure.batch.InterfaceHubBatchJobConfig;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
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

@Component
public class BatchInterfaceExecutor implements InterfaceExecutor {

    private final JobLauncher jobLauncher;
    private final Map<String, Job> jobsByName;
    private final ObjectMapper objectMapper;

    public BatchInterfaceExecutor(
        JobLauncher jobLauncher,
        List<Job> jobs,
        ObjectMapper objectMapper
    ) {
        this.jobLauncher = jobLauncher;
        this.jobsByName = jobs.stream().collect(Collectors.toMap(Job::getName, job -> job, (left, right) -> left));
        this.objectMapper = objectMapper;
    }

    @Override
    public ProtocolType supportType() {
        return ProtocolType.BATCH;
    }

    @Override
    public ExecutionResult execute(ExecutionContext context) {
        long start = System.currentTimeMillis();
        try {
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
            JobExecution execution = jobLauncher.run(job, params);

            if (execution.getStatus() == BatchStatus.COMPLETED) {
                String resultPayload = extractResultPayload(execution);
                return ExecutionResult.success(resultPayload, elapsed(start));
            }
            String reason = execution.getAllFailureExceptions().stream()
                .map(Throwable::getMessage)
                .filter(message -> message != null && !message.isBlank())
                .findFirst()
                .orElse("Batch execution status: " + execution.getStatus().name());
            return ExecutionResult.failure(ErrorCode.BATCH_FAILED.name(), reason, elapsed(start));
        } catch (Exception exception) {
            return ExecutionResult.failure(ErrorCode.BATCH_FAILED.name(), exception.getMessage(), elapsed(start));
        }
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

    private String extractResultPayload(JobExecution execution) throws Exception {
        for (StepExecution stepExecution : execution.getStepExecutions()) {
            String resultPayload = stepExecution.getExecutionContext().getString("resultPayload", null);
            if (resultPayload != null) {
                return resultPayload;
            }
        }
        return objectMapper.writeValueAsString(Map.of(
            "jobExecutionId", execution.getId(),
            "status", execution.getStatus().name()
        ));
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
