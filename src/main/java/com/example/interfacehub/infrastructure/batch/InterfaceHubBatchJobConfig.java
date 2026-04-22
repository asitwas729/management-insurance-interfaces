package com.example.interfacehub.infrastructure.batch;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.LocalDateTime;
import java.util.Map;
import org.springframework.batch.core.Job;
import org.springframework.batch.core.JobParameters;
import org.springframework.batch.core.Step;
import org.springframework.batch.core.job.builder.JobBuilder;
import org.springframework.batch.core.repository.JobRepository;
import org.springframework.batch.core.step.builder.StepBuilder;
import org.springframework.batch.repeat.RepeatStatus;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.transaction.PlatformTransactionManager;

@Configuration
public class InterfaceHubBatchJobConfig {

    public static final String JOB_NAME = "interfaceHubPayloadJob";

    @Bean(name = JOB_NAME)
    public Job interfaceHubPayloadJob(JobRepository jobRepository, Step interfaceHubPayloadStep) {
        return new JobBuilder(JOB_NAME, jobRepository)
            .start(interfaceHubPayloadStep)
            .build();
    }

    @Bean
    public Step interfaceHubPayloadStep(
        JobRepository jobRepository,
        PlatformTransactionManager transactionManager,
        ObjectMapper objectMapper
    ) {
        return new StepBuilder("interfaceHubPayloadStep", jobRepository)
            .tasklet((contribution, chunkContext) -> {
                JobParameters params = contribution.getStepExecution().getJobParameters();
                String interfaceCode = params.getString("interfaceCode", "UNKNOWN_IF");
                long itemCount = params.getLong("itemCount", 1L);
                boolean simulateFailure = Boolean.parseBoolean(params.getString("simulateFailure", "false"));
                String requestedBy = params.getString("requestedBy", "system");

                if (simulateFailure) {
                    throw new IllegalStateException("Batch job simulated failure by input parameter");
                }

                String resultPayload = objectMapper.writeValueAsString(Map.of(
                    "jobName", JOB_NAME,
                    "interfaceCode", interfaceCode,
                    "itemCount", itemCount,
                    "requestedBy", requestedBy,
                    "status", "COMPLETED",
                    "finishedAt", LocalDateTime.now().toString()
                ));
                contribution.getStepExecution().getExecutionContext().putString("resultPayload", resultPayload);
                return RepeatStatus.FINISHED;
            }, transactionManager)
            .build();
    }
}
