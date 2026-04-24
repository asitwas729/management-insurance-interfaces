package com.example.interfacehub.presentation;

import com.example.interfacehub.domain.interfaceconfig.InterfaceConfigVersion;
import com.example.interfacehub.domain.interfaceconfig.RuntimeEnvironment;
import java.time.LocalDateTime;

public record ConfigResponse(
    Long configId,
    String interfaceCode,
    Integer version,
    RuntimeEnvironment environment,
    String endpoint,
    String authType,
    String headersJson,
    String protocolConfigJson,
    String requestSample,
    String responseSample,
    String mappingRuleText,
    String fieldDescriptionText,
    String errorCodeGuideText,
    Long timeoutMillis,
    boolean published,
    boolean sandboxMode,
    LocalDateTime createdAt
) {
    public static ConfigResponse from(InterfaceConfigVersion config) {
        return new ConfigResponse(
            config.getId(),
            config.getInterfaceDefinition().getInterfaceCode(),
            config.getVersion(),
            config.getEnvironment(),
            config.getEndpoint(),
            config.getAuthType(),
            config.getHeadersJson(),
            config.getProtocolConfigJson(),
            config.getRequestSample(),
            config.getResponseSample(),
            config.getMappingRuleText(),
            config.getFieldDescriptionText(),
            config.getErrorCodeGuideText(),
            config.getTimeoutMillis(),
            config.isPublished(),
            config.isSandboxMode(),
            config.getCreatedAt()
        );
    }
}
