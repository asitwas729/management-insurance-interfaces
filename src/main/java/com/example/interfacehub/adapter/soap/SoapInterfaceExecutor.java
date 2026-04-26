package com.example.interfacehub.adapter.soap;

import com.example.interfacehub.application.execution.InterfaceExecutor;
import com.example.interfacehub.application.standardmessage.StandardMessageValidationService;
import com.example.interfacehub.application.standardmessage.StandardValidationResult;
import com.example.interfacehub.common.error.ErrorCode;
import com.example.interfacehub.domain.execution.ExecutionContext;
import com.example.interfacehub.domain.execution.ExecutionResult;
import com.example.interfacehub.domain.interfaceconfig.ProtocolType;
import com.example.interfacehub.infrastructure.resilience.ExternalCallResilienceService;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.Tracer;
import io.opentelemetry.context.Scope;
import java.io.StringReader;
import java.io.StringWriter;
import java.net.SocketTimeoutException;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import javax.xml.XMLConstants;
import javax.xml.transform.Result;
import javax.xml.transform.Source;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.stream.StreamResult;
import javax.xml.transform.stream.StreamSource;
import org.springframework.oxm.Marshaller;
import org.springframework.oxm.Unmarshaller;
import org.springframework.stereotype.Component;
import org.springframework.util.MultiValueMap;
import org.springframework.ws.WebServiceMessage;
import org.springframework.ws.client.WebServiceIOException;
import org.springframework.ws.client.WebServiceTransportException;
import org.springframework.ws.client.core.WebServiceMessageCallback;
import org.springframework.ws.client.core.WebServiceTemplate;
import org.springframework.ws.soap.SoapMessage;
import org.springframework.ws.soap.client.SoapFaultClientException;
import org.springframework.ws.transport.http.HttpUrlConnectionMessageSender;

@Component
public class SoapInterfaceExecutor implements InterfaceExecutor {

    private static final String DEFAULT_NAMESPACE = "http://interfacehub.example.com/soap";
    private static final String DEFAULT_OPERATION = "ExecuteRequest";
    private static final String SOAP_ACTION = "SOAPAction";
    private static final String HEADER_SOAP_ACTION = "X-SOAP-Action";
    private static final String HEADER_SOAP_NAMESPACE = "X-SOAP-Namespace";
    private static final String HEADER_SOAP_OPERATION = "X-SOAP-Operation";

    private final RawXmlOxMapper rawXmlOxMapper;
    private final ExternalCallResilienceService resilienceService;
    private final StandardMessageValidationService standardMessageValidationService;
    private final ObjectMapper objectMapper;
    private final MeterRegistry meterRegistry; // Add MeterRegistry
    private final Tracer tracer; // Add Tracer

    public SoapInterfaceExecutor(
        ExternalCallResilienceService resilienceService,
        StandardMessageValidationService standardMessageValidationService,
        ObjectMapper objectMapper,
        MeterRegistry meterRegistry, // Inject MeterRegistry
        Tracer tracer // Inject Tracer
    ) {
        this.rawXmlOxMapper = new RawXmlOxMapper();
        this.resilienceService = resilienceService;
        this.standardMessageValidationService = standardMessageValidationService;
        this.objectMapper = objectMapper;
        this.meterRegistry = meterRegistry;
        this.tracer = tracer; // Assign Tracer
    }

    @Override
    public ProtocolType supportType() {
        return ProtocolType.SOAP;
    }

    @Override
    public ExecutionResult execute(ExecutionContext context) {
        // Create a span for the SOAP execution
        Span span = tracer.spanBuilder("SoapInterfaceExecutor.execute").startSpan();
        ExecutionResult result;
        try (Scope scope = span.makeCurrent()) {
            // Record metrics for execution latency and outcome
            Timer timer = Timer.builder("execution.soap.latency")
                .tag("interfaceCode", context.interfaceCode())
                .tag("outcome", "unknown") // Will be updated after execution
                .register(meterRegistry);

            SoapRequest request = toSoapRequest(context);
            ExecutionResult validationFailure = validateStandardMessageIfRequired(context, request.bodyXml());
            if (validationFailure != null) {
                result = validationFailure;
            } else {
                result = resilienceService.execute(context.interfaceCode(), () -> {
                    ExecutionResult invokeResult = invoke(context, request);
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
                }, ErrorCode.SOAP_CALL_FAILED);
            }

            // Record timer after resilience service completes
            String outcome = result.success() ? "success" : "failure";
            timer.record(Duration.ofMillis(result.latencyMillis()));
            meterRegistry.counter("execution.soap.count", "interfaceCode", context.interfaceCode(), "outcome", outcome).increment();

        } catch (Throwable t) { // Catching Throwable to include potential RuntimeExceptions from resilienceService
            // Handle exceptions from resilienceService or the lambda itself
            span.setStatus(io.opentelemetry.api.trace.StatusCode.ERROR);
            span.setAttribute("execution.outcome", "failure");
            if (t instanceof SoapFaultClientException) {
                span.setAttribute("execution.error.code", ErrorCode.SOAP_FAULT.name());
            } else if (t instanceof WebServiceTransportException) {
                span.setAttribute("execution.error.code", resolveTransportErrorCode((WebServiceTransportException) t));
            } else if (t instanceof WebServiceIOException) {
                span.setAttribute("execution.error.code", resolveIoErrorCode((WebServiceIOException) t));
            } else {
                span.setAttribute("execution.error.code", ErrorCode.SOAP_CALL_FAILED.name());
            }
            span.recordException(t);
            // Re-throw to ensure the orchestrator gets the exception
            throw t;
        } finally {
            span.end();
        }
        return result;
    }

    private ExecutionResult validateStandardMessageIfRequired(ExecutionContext context, String xml) {
        StandardMessageValidationService.StandardSchemaRef ref =
            standardMessageValidationService.resolveFromProtocolConfigJson(context.protocolConfigJson());
        if (!ref.enabled()) {
            return null;
        }

        StandardValidationResult result = standardMessageValidationService.validate(
            ref.schemaCode(),
            ref.version(),
            xml,
            ref.enforceRules()
        );
        if (result.valid()) {
            return null;
        }

        String message = result.errors().isEmpty()
            ? "Standard validation failed"
            : result.errors().get(0).message();
        return ExecutionResult.failure(ErrorCode.STANDARD_VALIDATION_FAILED.name(), message, 0L);
    }

    private ExecutionResult invoke(ExecutionContext context, SoapRequest request) {
        long start = System.currentTimeMillis();
        try {
            WebServiceTemplate requestTemplate = createRequestTemplate(context.timeoutMillis());
            Object response = requestTemplate.marshalSendAndReceive(
                context.endpoint(),
                new RawXmlPayload(request.bodyXml()),
                soapActionCallback(request.soapAction())
            );
            return ExecutionResult.success(response == null ? "" : response.toString(), elapsed(start));
        } catch (SoapFaultClientException exception) {
            return ExecutionResult.failure(ErrorCode.SOAP_CALL_FAILED.name(), exception.getFaultStringOrReason(), elapsed(start));
        } catch (WebServiceTransportException exception) {
            return ExecutionResult.failure(resolveTransportErrorCode(exception), exception.getMessage(), elapsed(start));
        } catch (WebServiceIOException exception) {
            return ExecutionResult.failure(resolveIoErrorCode(exception), exception.getMessage(), elapsed(start));
        } catch (RuntimeException exception) {
            return ExecutionResult.failure(ErrorCode.SOAP_CALL_FAILED.name(), exception.getMessage(), elapsed(start));
        }
    }

    private WebServiceTemplate createRequestTemplate(long timeoutMillis) {
        WebServiceTemplate requestTemplate = new WebServiceTemplate();
        requestTemplate.setMarshaller(rawXmlOxMapper);
        requestTemplate.setUnmarshaller(rawXmlOxMapper);
        HttpUrlConnectionMessageSender messageSender = new HttpUrlConnectionMessageSender();
        Duration timeout = Duration.ofMillis(timeoutMillis);
        messageSender.setConnectionTimeout(timeout);
        messageSender.setReadTimeout(timeout);
        requestTemplate.setMessageSender(messageSender);
        return requestTemplate;
    }

    private WebServiceMessageCallback soapActionCallback(String soapAction) {
        return message -> {
            applySoapHeaders(message, soapAction);
        };
    }

    private void applySoapHeaders(WebServiceMessage message, String soapAction) {
        if (soapAction == null || soapAction.isBlank() || !(message instanceof SoapMessage soapMessage)) {
            return;
        }
        soapMessage.setSoapAction(soapAction);
    }

    private SoapRequest toSoapRequest(ExecutionContext context) {
        if (context.payload() == null || context.payload().isBlank()) {
            return new SoapRequest(defaultBody(DEFAULT_OPERATION, DEFAULT_NAMESPACE, Map.of("payload", "")), resolveHeader(context.headers(), SOAP_ACTION));
        }

        try {
            Map<String, Object> parsed = objectMapper.readValue(context.payload(), new TypeReference<>() {
            });
            String soapAction = firstNonBlank(
                stringValue(parsed.get("soapAction")),
                resolveHeader(context.headers(), SOAP_ACTION),
                resolveHeader(context.headers(), HEADER_SOAP_ACTION)
            );
            String rawSoapXml = stringValue(parsed.get("soapXml"));
            if (rawSoapXml != null && !rawSoapXml.isBlank()) {
                return new SoapRequest(toBodyPayload(rawSoapXml), soapAction);
            }

            String namespace = firstNonBlank(
                stringValue(parsed.get("namespace")),
                resolveHeader(context.headers(), HEADER_SOAP_NAMESPACE),
                DEFAULT_NAMESPACE
            );
            String operation = firstNonBlank(
                stringValue(parsed.get("operation")),
                stringValue(parsed.get("operationName")),
                resolveHeader(context.headers(), HEADER_SOAP_OPERATION),
                DEFAULT_OPERATION
            );
            Object body = parsed.getOrDefault("body", withoutSoapMetadata(parsed));
            return new SoapRequest(defaultBody(operation, namespace, body), soapAction);
        } catch (Exception ignored) {
            String soapAction = firstNonBlank(resolveHeader(context.headers(), SOAP_ACTION), resolveHeader(context.headers(), HEADER_SOAP_ACTION));
            String rawPayload = context.payload().trim();
            String bodyXml = rawPayload.startsWith("<")
                ? toBodyPayload(rawPayload)
                : defaultBody(DEFAULT_OPERATION, DEFAULT_NAMESPACE, Map.of("payload", context.payload()));
            return new SoapRequest(bodyXml, soapAction);
        }
    }

    private String toBodyPayload(String xml) {
        if (xml == null) {
            return "";
        }
        String trimmed = xml.trim();
        if (!trimmed.matches("(?is).*<[^>]*:?Envelope\\b.*")) {
            return trimmed;
        }
        return trimmed.replaceFirst("(?is)^.*<[^>]*:?Body[^>]*>", "")
            .replaceFirst("(?is)</[^>]*:?Body>.*$", "")
            .trim();
    }

    private Map<String, Object> withoutSoapMetadata(Map<String, Object> parsed) {
        Map<String, Object> result = new LinkedHashMap<>(parsed);
        result.remove("soapAction");
        result.remove("soapXml");
        result.remove("namespace");
        result.remove("operation");
        result.remove("operationName");
        return result;
    }

    private String defaultBody(String operation, String namespace, Object body) {
        String nsPrefix = "ifh";
        return "<%s:%s xmlns:%s=\"%s\">%s</%s:%s>".formatted(
            nsPrefix,
            safeXmlName(operation, DEFAULT_OPERATION),
            nsPrefix,
            escapeXml(namespace),
            toXmlElements(body),
            nsPrefix,
            safeXmlName(operation, DEFAULT_OPERATION)
        );
    }

    @SuppressWarnings("unchecked")
    private String toXmlElements(Object value) {
        if (value == null) {
            return "";
        }
        if (value instanceof Map<?, ?> map) {
            StringBuilder builder = new StringBuilder();
            map.forEach((key, childValue) -> {
                String rawKey = String.valueOf(key);
                String elementName = safeXmlName(rawKey);
                if (elementName == null) {
                    builder.append("<field name=\"").append(escapeXml(rawKey)).append("\">")
                        .append(toXmlElements(childValue))
                        .append("</field>");
                    return;
                }
                builder.append("<").append(elementName).append(">")
                    .append(toXmlElements(childValue))
                    .append("</").append(elementName).append(">");
            });
            return builder.toString();
        }
        if (value instanceof List<?> list) {
            StringBuilder builder = new StringBuilder();
            for (Object item : list) {
                builder.append("<item>").append(toXmlElements(item)).append("</item>");
            }
            return builder.toString();
        }
        return escapeXml(String.valueOf(value));
    }

    private String resolveHeader(MultiValueMap<String, String> headers, String headerName) {
        if (headers == null || headerName == null) {
            return null;
        }
        String exact = headers.getFirst(headerName);
        if (exact != null) {
            return exact;
        }
        for (Map.Entry<String, List<String>> entry : headers.entrySet()) {
            if (headerName.equalsIgnoreCase(entry.getKey()) && !entry.getValue().isEmpty()) {
                return entry.getValue().get(0);
            }
        }
        return null;
    }

    private String resolveTransportErrorCode(WebServiceTransportException exception) {
        String message = exception.getMessage();
        if (message != null && message.matches(".*\\b4\\d\\d\\b.*")) {
            return ErrorCode.EXT_4XX.name();
        }
        if (message != null && message.matches(".*\\b5\\d\\d\\b.*")) {
            return ErrorCode.EXT_5XX.name();
        }
        return ErrorCode.SOAP_CALL_FAILED.name();
    }

    private String resolveIoErrorCode(WebServiceIOException exception) {
        Throwable cause = exception.getCause();
        while (cause != null) {
            if (cause instanceof SocketTimeoutException) {
                return ErrorCode.TIMEOUT.name();
            }
            cause = cause.getCause();
        }
        return ErrorCode.SOAP_CALL_FAILED.name();
    }

    private String firstNonBlank(String... values) {
        for (String value : values) {
            if (value != null && !value.isBlank()) {
                return value;
            }
        }
        return null;
    }

    private String stringValue(Object value) {
        return value == null ? null : String.valueOf(value);
    }

    private String safeXmlName(String candidate, String fallback) {
        String safeName = safeXmlName(candidate);
        return safeName == null ? fallback : safeName;
    }

    private String safeXmlName(String candidate) {
        if (candidate == null || !candidate.matches("[A-Za-z_][A-Za-z0-9_.-]*")) {
            return null;
        }
        return candidate;
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

    private static class RawXmlOxMapper implements Marshaller, Unmarshaller {

        @Override
        public boolean supports(Class<?> clazz) {
            return RawXmlPayload.class.isAssignableFrom(clazz);
        }

        @Override
        public void marshal(Object graph, Result result) {
            RawXmlPayload payload = (RawXmlPayload) graph;
            transform(new StreamSource(new StringReader(payload.xml())), result);
        }

        @Override
        public Object unmarshal(Source source) {
            StringWriter writer = new StringWriter();
            transform(source, new StreamResult(writer));
            return writer.toString();
        }

        private void transform(Source source, Result result) {
            try {
                TransformerFactory factory = TransformerFactory.newInstance();
                factory.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
                factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_DTD, "");
                factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_STYLESHEET, "");
                factory.newTransformer().transform(source, result);
            } catch (Exception exception) {
                throw new IllegalArgumentException("SOAP XML transform failed", exception);
            }
        }
    }
}
