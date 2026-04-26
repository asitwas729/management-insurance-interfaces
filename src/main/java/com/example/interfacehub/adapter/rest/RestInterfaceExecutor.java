package com.example.interfacehub.adapter.rest;

import com.example.interfacehub.application.fixedlength.FixedLengthConfig;
import com.example.interfacehub.application.fixedlength.FixedLengthMessageService;
import com.example.interfacehub.application.execution.InterfaceExecutor;
import com.example.interfacehub.common.error.BusinessException;
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
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientRequestException;
import org.springframework.web.reactive.function.client.WebClientResponseException;

@Component
public class RestInterfaceExecutor implements InterfaceExecutor {

    private final WebClient.Builder webClientBuilder;
    private final ExternalCallResilienceService resilienceService;
    private final MeterRegistry meterRegistry; // Add MeterRegistry
    private final Tracer tracer; // Add Tracer
    private final FixedLengthMessageService fixedLengthMessageService;

    public RestInterfaceExecutor(
        WebClient.Builder webClientBuilder,
        ExternalCallResilienceService resilienceService,
        MeterRegistry meterRegistry, // Inject MeterRegistry
        Tracer tracer, // Inject Tracer
        FixedLengthMessageService fixedLengthMessageService
    ) {
        this.webClientBuilder = webClientBuilder;
        this.resilienceService = resilienceService;
        this.meterRegistry = meterRegistry;
        this.tracer = tracer; // Assign Tracer
        this.fixedLengthMessageService = fixedLengthMessageService;
    }

    @Override
    public ProtocolType supportType() {
        return ProtocolType.REST;
    }

    @Override
    public ExecutionResult execute(ExecutionContext context) {
        // Create a span for the REST execution
        Span span = tracer.spanBuilder("RestInterfaceExecutor.execute").startSpan();
        ExecutionResult result;
        try (Scope scope = span.makeCurrent()) {
            // Record metrics for execution latency and outcome
            Timer timer = Timer.builder("execution.rest.latency")
                .tag("interfaceCode", context.interfaceCode())
                .tag("outcome", "unknown") // Will be updated after execution
                .register(meterRegistry);

            long startTime = System.currentTimeMillis();
            result = resilienceService.execute(context.interfaceCode(), () -> {
                ExecutionResult invokeResult = invoke(context);
                // Update span status and tags based on the outcome
                if (invokeResult.success()) {
                    span.setStatus(io.opentelemetry.api.trace.StatusCode.OK);
                    span.setAttribute("execution.outcome", "success");
                    span.setAttribute("execution.latency", invokeResult.latencyMillis());
                } else {
                    span.setStatus(io.opentelemetry.api.trace.StatusCode.ERROR);
                    span.setAttribute("execution.outcome", "failure");
                    span.setAttribute("execution.error.code", invokeResult.errorCode());
                    span.setAttribute("execution.latency", invokeResult.latencyMillis());
                }
                return invokeResult;
            }, ErrorCode.REST_CALL_FAILED);

            // Record timer after resilience service completes
            String outcome = result.success() ? "success" : "failure";
            timer.record(Duration.ofMillis(result.latencyMillis()));
            meterRegistry.counter("execution.rest.count", "interfaceCode", context.interfaceCode(), "outcome", outcome).increment();

        } catch (Throwable t) { // Catching Throwable to include potential RuntimeExceptions from resilienceService
            // Handle exceptions from resilienceService or the lambda itself
            span.setStatus(io.opentelemetry.api.trace.StatusCode.ERROR);
            span.setAttribute("execution.outcome", "failure");
            if (t instanceof WebClientResponseException) {
                WebClientResponseException wcre = (WebClientResponseException) t;
                span.setAttribute("http.status_code", wcre.getStatusCode().value());
                span.setAttribute("execution.error.code", wcre.getStatusCode().is5xxServerError() ? ErrorCode.EXT_5XX.name() : ErrorCode.EXT_4XX.name());
            } else if (t instanceof WebClientRequestException) {
                span.setAttribute("execution.error.code", ErrorCode.REST_CALL_FAILED.name());
            } else if (t instanceof IllegalStateException) { // Timeout
                span.setAttribute("execution.error.code", ErrorCode.TIMEOUT.name());
            } else {
                span.setAttribute("execution.error.code", ErrorCode.INTERNAL_ERROR.name());
            }
            span.recordException(t);
            // Re-throw to ensure the orchestrator gets the exception
            throw t;
        } finally {
            span.end();
        }
        return result;
    }

    private ExecutionResult invoke(ExecutionContext context) {
        long start = System.currentTimeMillis();
        try {
            FixedLengthConfig fixed = fixedLengthMessageService.resolve(context.protocolConfigJson());

            WebClient.RequestBodySpec request = webClientBuilder.build()
                .post()
                .uri(context.endpoint())
                .headers(headers -> headers.addAll(context.headers()));

            if (fixed.enforceRequest() && fixed.requestSchema() != null) {
                byte[] bodyBytes = fixedLengthMessageService.encodeJsonPayloadToFixedBytes(context.payload(), fixed.requestSchema());
                String charset = fixed.requestSchema().charset() == null ? "UTF-8" : fixed.requestSchema().charset();
                request = request.contentType(MediaType.parseMediaType("text/plain;charset=" + charset));

                if (fixed.enforceResponse() && fixed.responseSchema() != null) {
                    byte[] responseBytes = request
                        .bodyValue(bodyBytes)
                        .retrieve()
                        .bodyToMono(byte[].class)
                        .block(Duration.ofMillis(context.timeoutMillis()));
                    String decodedJson = fixedLengthMessageService.decodeFixedBytesToJson(responseBytes, fixed.responseSchema());
                    return ExecutionResult.success(decodedJson, elapsed(start));
                }

                String response = request
                    .bodyValue(bodyBytes)
                    .retrieve()
                    .bodyToMono(String.class)
                    .block(Duration.ofMillis(context.timeoutMillis()));
                return ExecutionResult.success(response, elapsed(start));
            }

            if (fixed.enforceResponse() && fixed.responseSchema() != null) {
                byte[] responseBytes = request
                    .bodyValue(context.payload())
                    .retrieve()
                    .bodyToMono(byte[].class)
                    .block(Duration.ofMillis(context.timeoutMillis()));
                String decodedJson = fixedLengthMessageService.decodeFixedBytesToJson(responseBytes, fixed.responseSchema());
                return ExecutionResult.success(decodedJson, elapsed(start));
            }

            String response = request
                .bodyValue(context.payload())
                .retrieve()
                .bodyToMono(String.class)
                .block(Duration.ofMillis(context.timeoutMillis()));

            return ExecutionResult.success(response, elapsed(start));
        } catch (BusinessException exception) {
            return ExecutionResult.failure(exception.getErrorCode().name(), exception.getMessage(), elapsed(start));
        } catch (IllegalStateException exception) {
            // block() timeout
            return ExecutionResult.failure(ErrorCode.TIMEOUT.name(), exception.getMessage(), elapsed(start));
        } catch (WebClientResponseException exception) {
            if (exception.getStatusCode().is4xxClientError()) {
                return ExecutionResult.failure(ErrorCode.EXT_4XX.name(), exception.getMessage(), elapsed(start));
            }
            if (exception.getStatusCode().is5xxServerError()) {
                return ExecutionResult.failure(ErrorCode.EXT_5XX.name(), exception.getMessage(), elapsed(start));
            }
            return ExecutionResult.failure(ErrorCode.REST_CALL_FAILED.name(), exception.getMessage(), elapsed(start));
        } catch (WebClientRequestException exception) {
            return ExecutionResult.failure(ErrorCode.REST_CALL_FAILED.name(), exception.getMessage(), elapsed(start));
        } catch (RuntimeException exception) {
            return ExecutionResult.failure(ErrorCode.REST_CALL_FAILED.name(), exception.getMessage(), elapsed(start));
        }
    }

    private long elapsed(long start) {
        return System.currentTimeMillis() - start;
    }
}
