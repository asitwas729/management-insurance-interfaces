package com.example.interfacehub.presentation;

import com.example.interfacehub.domain.policy.PolicyAuthType;
import com.example.interfacehub.domain.policy.PolicyTemplate;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Set;

public record PolicyTemplateResponse(
    Long id,
    String policyName,
    PolicyAuthType authType,
    Long timeoutMillis,
    Integer retryMaxAttempts,
    Long retryIntervalMillis,
    Integer rateLimitPerMinute,
    Set<String> allowedPartnerIds,
    Set<String> allowedRoles,
    boolean maskRequestPayload,
    boolean maskResponsePayload,
    boolean enabled
) {

    public static PolicyTemplateResponse from(PolicyTemplate template, ObjectMapper objectMapper) {
        return new PolicyTemplateResponse(
            template.getId(),
            template.getPolicyName(),
            template.getAuthType(),
            template.getTimeoutMillis(),
            template.getRetryMaxAttempts(),
            template.getRetryIntervalMillis(),
            template.getRateLimitPerMinute(),
            toSet(template.getAllowedPartnerIdsJson(), objectMapper),
            toSet(template.getAllowedRolesJson(), objectMapper),
            template.isMaskRequestPayload(),
            template.isMaskResponsePayload(),
            template.isEnabled()
        );
    }

    private static Set<String> toSet(String json, ObjectMapper objectMapper) {
        if (json == null || json.isBlank()) {
            return Set.of();
        }
        try {
            return Set.copyOf(objectMapper.readValue(json, new TypeReference<Set<String>>() {
            }));
        } catch (JsonProcessingException exception) {
            return Set.of();
        }
    }
}
