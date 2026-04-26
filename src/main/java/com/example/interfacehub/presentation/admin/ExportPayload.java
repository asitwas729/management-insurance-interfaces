package com.example.interfacehub.presentation.admin;

import com.example.interfacehub.domain.interfaceconfig.CallDirection;
import com.example.interfacehub.domain.interfaceconfig.ProtocolType;
import com.example.interfacehub.domain.interfaceconfig.RuntimeEnvironment;
import com.example.interfacehub.domain.policy.PolicyAuthType;
import java.time.LocalDateTime;
import java.util.List;

public record ExportPayload(
    LocalDateTime exportedAt,
    List<InterfaceExportEntry> interfaces,
    List<ErrorCatalogExport> errorCatalog,
    List<PolicyTemplateExport> policyTemplates
) {

    public record InterfaceExportEntry(
        InterfaceDefinitionExport definition,
        List<InterfaceConfigVersionExport> configVersions
    ) {
    }

    public record InterfaceDefinitionExport(
        String interfaceCode,
        String name,
        ProtocolType protocolType,
        String ownerTeam,
        String businessCategory,
        String externalOrg,
        CallDirection callDirection,
        Long slaMillis
    ) {
    }

    public record InterfaceConfigVersionExport(
        Integer version,
        String endpoint,
        String authType,
        String headersJson,
        Long timeoutMillis,
        RuntimeEnvironment environment,
        String protocolConfigJson,
        String requestSample,
        String responseSample,
        String mappingRuleText,
        String fieldDescriptionText,
        String errorCodeGuideText,
        boolean sandboxMode,
        Integer mockHttpStatus,
        String mockResponseBody,
        boolean published
    ) {
    }

    public record ErrorCatalogExport(
        String code,
        String domain,
        String severity,
        int httpStatus,
        boolean retriable,
        String nextAction,
        String description
    ) {
    }

    public record PolicyTemplateExport(
        String policyName,
        PolicyAuthType authType,
        Long timeoutMillis,
        Integer retryMaxAttempts,
        Long retryIntervalMillis,
        Integer rateLimitPerMinute,
        String allowedPartnerIdsJson,
        boolean maskRequestPayload,
        boolean maskResponsePayload,
        String allowedRolesJson,
        boolean enabled
    ) {
    }
}
