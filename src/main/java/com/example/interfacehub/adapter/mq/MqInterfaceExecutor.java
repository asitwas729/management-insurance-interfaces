package com.example.interfacehub.adapter.mq;

import com.example.interfacehub.application.execution.InterfaceExecutor;
import com.example.interfacehub.common.error.ErrorCode;
import com.example.interfacehub.domain.execution.ExecutionContext;
import com.example.interfacehub.domain.execution.ExecutionResult;
import com.example.interfacehub.domain.interfaceconfig.ProtocolType;
import com.example.interfacehub.infrastructure.resilience.ExternalCallResilienceService;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.Tracer;
import io.opentelemetry.context.Scope;
import java.time.Duration;
import org.springframework.stereotype.Component;

@Component
public class MqInterfaceExecutor implements InterfaceExecutor {

    private final MqGateway mqGateway;
    private final ExternalCallResilienceService resilienceService;
    private final MeterRegistry meterRegistry; // Add MeterRegistry
    private final Tracer tracer; // Add Tracer

    public MqInterfaceExecutor(
        MqGateway mqGateway,
        ExternalCallResilienceService resilienceService,
        MeterRegistry meterRegistry, // Inject MeterRegistry
        Tracer tracer // Inject Tracer
    ) {
        this.mqGateway = mqGateway;
        this.resilienceService = resilienceService;
        this.meterRegistry = meterRegistry;
        this.tracer = tracer; // Assign Tracer
    }

    @Override
    public ProtocolType supportType() {
        return ProtocolType.MQ;
    }

    @Override
    public ExecutionResult execute(ExecutionContext context) {
        // Create a span for the MQ execution
        Span span = tracer.spanBuilder("MqInterfaceExecutor.execute").startSpan();
        ExecutionResult result;
        try (Scope scope = span.makeCurrent()) {
            // Record metrics for execution latency and outcome
            Timer timer = Timer.builder("execution.mq.latency")
                .tag("interfaceCode", context.interfaceCode())
                .tag("outcome", "unknown") // Will be updated after execution
                .register(meterRegistry);

            long startTime = System.currentTimeMillis();
            result = resilienceService.execute(context.interfaceCode(), () -> {
                MqProcessResult invokeResult = mqGateway.publish(
                    context.interfaceCode(),
                    context.endpoint(),
                    context.payload()
                );
                ExecutionResult executionResult = invokeResult.success()
                    ? ExecutionResult.success(invokeResult.responsePayload(), System.currentTimeMillis() - startTime)
                    : ExecutionResult.failure(ErrorCode.MQ_CONSUME_FAILED.name(), invokeResult.errorMessage(), System.currentTimeMillis() - startTime);

                // Update span status and tags based on the outcome
                if (executionResult.success()) {
                    span.setStatus(io.opentelemetry.api.trace.StatusCode.OK);
                    span.setAttribute("execution.outcome", "success");
                    span.setAttribute("execution.latency", executionResult.latencyMillis());
                } else {
                    span.setStatus(io.opentelemetry.api.trace.StatusCode.ERROR);
                    span.setAttribute("execution.outcome", "failure");
                    span.setAttribute("execution.error.code", executionResult.errorCode());
                    span.setAttribute("execution.latency", executionResult.latencyMillis());
                }
                return executionResult;
            }, ErrorCode.MQ_CONSUME_FAILED);

            // Record timer after resilience service completes
            String outcome = result.success() ? "success" : "failure";
            timer.record(Duration.ofMillis(result.latencyMillis()));
            meterRegistry.counter("execution.mq.count", "interfaceCode", context.interfaceCode(), "outcome", outcome).increment();

        } catch (Throwable t) { // Catching Throwable to include potential RuntimeExceptions from resilienceService
            // Handle exceptions from resilienceService or the lambda itself
            span.setStatus(io.opentelemetry.api.trace.StatusCode.ERROR);
            span.setAttribute("execution.outcome", "failure");
            span.setAttribute("execution.error.code", ErrorCode.MQ_CONSUME_FAILED.name()); // Default error code for MQ
            span.recordException(t);
            // Re-throw to ensure the orchestrator gets the exception
            throw t;
        } finally {
            span.end();
        }
        return result;
    }
}
