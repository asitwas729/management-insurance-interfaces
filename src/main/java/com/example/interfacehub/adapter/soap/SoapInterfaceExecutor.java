package com.example.interfacehub.adapter.soap;

import com.example.interfacehub.application.execution.InterfaceExecutor;
import com.example.interfacehub.common.error.ErrorCode;
import com.example.interfacehub.domain.execution.ExecutionContext;
import com.example.interfacehub.domain.execution.ExecutionResult;
import com.example.interfacehub.domain.interfaceconfig.ProtocolType;
import com.example.interfacehub.infrastructure.resilience.ExternalCallResilienceService;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Duration;
import java.util.Map;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientRequestException;
import org.springframework.web.reactive.function.client.WebClientResponseException;

/**
 * SOAP 어댑터 스켈레톤.
 * 실제 구현 시 Spring-WS WebServiceTemplate 기반으로 대체한다.
 */
@Component
public class SoapInterfaceExecutor implements InterfaceExecutor {

    private final WebClient.Builder webClientBuilder;
    private final ExternalCallResilienceService resilienceService;
    private final ObjectMapper objectMapper;

    public SoapInterfaceExecutor(
        WebClient.Builder webClientBuilder,
        ExternalCallResilienceService resilienceService,
        ObjectMapper objectMapper
    ) {
        this.webClientBuilder = webClientBuilder;
        this.resilienceService = resilienceService;
        this.objectMapper = objectMapper;
    }

    @Override
    public ProtocolType supportType() {
        return ProtocolType.SOAP;
    }

    @Override
    public ExecutionResult execute(ExecutionContext context) {
        return resilienceService.execute(context.interfaceCode(), () -> invoke(context), ErrorCode.SOAP_CALL_FAILED);
    }

    private ExecutionResult invoke(ExecutionContext context) {
        long start = System.currentTimeMillis();
        try {
            String response = webClientBuilder.build()
                .post()
                .uri(context.endpoint())
                .headers(headers -> {
                    headers.addAll(context.headers());
                    headers.set("Content-Type", "text/xml;charset=UTF-8");
                })
                .bodyValue(toSoapBody(context.payload()))
                .retrieve()
                .bodyToMono(String.class)
                .block(Duration.ofMillis(context.timeoutMillis()));

            return ExecutionResult.success(response, elapsed(start));
        } catch (IllegalStateException exception) {
            return ExecutionResult.failure(ErrorCode.TIMEOUT.name(), exception.getMessage(), elapsed(start));
        } catch (WebClientResponseException exception) {
            if (exception.getStatusCode().is4xxClientError()) {
                return ExecutionResult.failure(ErrorCode.EXT_4XX.name(), exception.getMessage(), elapsed(start));
            }
            if (exception.getStatusCode().is5xxServerError()) {
                return ExecutionResult.failure(ErrorCode.EXT_5XX.name(), exception.getMessage(), elapsed(start));
            }
            return ExecutionResult.failure(ErrorCode.SOAP_CALL_FAILED.name(), exception.getMessage(), elapsed(start));
        } catch (WebClientRequestException exception) {
            return ExecutionResult.failure(ErrorCode.SOAP_CALL_FAILED.name(), exception.getMessage(), elapsed(start));
        } catch (RuntimeException exception) {
            return ExecutionResult.failure(ErrorCode.SOAP_CALL_FAILED.name(), exception.getMessage(), elapsed(start));
        }
    }

    private String toSoapBody(String payload) {
        if (payload == null || payload.isBlank()) {
            return defaultEnvelope("{}");
        }
        try {
            Map<String, Object> parsed = objectMapper.readValue(payload, new TypeReference<>() {
            });
            Object soapXml = parsed.get("soapXml");
            if (soapXml instanceof String soapRaw && !soapRaw.isBlank()) {
                return soapRaw;
            }
        } catch (Exception ignored) {
            // fall back to wrapping payload
        }
        return defaultEnvelope(payload);
    }

    private String defaultEnvelope(String payload) {
        return """
            <soapenv:Envelope xmlns:soapenv="http://schemas.xmlsoap.org/soap/envelope/">
              <soapenv:Body>
                <ifh:ExecuteRequest xmlns:ifh="http://interfacehub.example.com/soap">
                  <ifh:payload>%s</ifh:payload>
                </ifh:ExecuteRequest>
              </soapenv:Body>
            </soapenv:Envelope>
            """.formatted(escapeXml(payload));
    }

    private String escapeXml(String raw) {
        return raw
            .replace("&", "&amp;")
            .replace("<", "&lt;")
            .replace(">", "&gt;")
            .replace("\"", "&quot;")
            .replace("'", "&apos;");
    }

    private long elapsed(long start) {
        return System.currentTimeMillis() - start;
    }
}
