package com.example.interfacehub.adapter.rest;

import com.example.interfacehub.application.execution.InterfaceExecutor;
import com.example.interfacehub.common.error.ErrorCode;
import com.example.interfacehub.domain.execution.ExecutionContext;
import com.example.interfacehub.domain.execution.ExecutionResult;
import com.example.interfacehub.domain.interfaceconfig.ProtocolType;
import com.example.interfacehub.infrastructure.resilience.ExternalCallResilienceService;
import java.time.Duration;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientRequestException;
import org.springframework.web.reactive.function.client.WebClientResponseException;

@Component
public class RestInterfaceExecutor implements InterfaceExecutor {

    private final WebClient.Builder webClientBuilder;
    private final ExternalCallResilienceService resilienceService;

    public RestInterfaceExecutor(
        WebClient.Builder webClientBuilder,
        ExternalCallResilienceService resilienceService
    ) {
        this.webClientBuilder = webClientBuilder;
        this.resilienceService = resilienceService;
    }

    @Override
    public ProtocolType supportType() {
        return ProtocolType.REST;
    }

    @Override
    public ExecutionResult execute(ExecutionContext context) {
        return resilienceService.execute(context.interfaceCode(), () -> invoke(context), ErrorCode.REST_CALL_FAILED);
    }

    private ExecutionResult invoke(ExecutionContext context) {
        long start = System.currentTimeMillis();
        try {
            String response = webClientBuilder.build()
                .post()
                .uri(context.endpoint())
                .headers(headers -> headers.addAll(context.headers()))
                .bodyValue(context.payload())
                .retrieve()
                .bodyToMono(String.class)
                .block(Duration.ofMillis(context.timeoutMillis()));

            return ExecutionResult.success(response, elapsed(start));
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
