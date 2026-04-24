package com.example.interfacehub.application.policy;

import com.example.interfacehub.common.error.BusinessException;
import com.example.interfacehub.common.error.ErrorCode;
import com.example.interfacehub.domain.interfaceconfig.InterfaceDefinition;
import com.example.interfacehub.domain.policy.InterfacePolicyBinding;
import com.example.interfacehub.domain.policy.PolicyAuthType;
import com.example.interfacehub.domain.policy.PolicyTemplate;
import com.example.interfacehub.domain.policy.RuntimePolicySnapshot;
import com.example.interfacehub.infrastructure.persistence.InterfacePolicyBindingRepository;
import com.example.interfacehub.infrastructure.persistence.PolicyTemplateRepository;
import com.example.interfacehub.infrastructure.persistence.RuntimePolicySnapshotRepository;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class PolicyEnforcementService {

    private static final String DEFAULT_POLICY_NAME = "DEFAULT";

    private final InterfacePolicyBindingRepository interfacePolicyBindingRepository;
    private final PolicyTemplateRepository policyTemplateRepository;
    private final RuntimePolicySnapshotRepository runtimePolicySnapshotRepository;
    private final InMemoryRateLimitService rateLimitService;
    private final ObjectMapper objectMapper;

    public PolicyEnforcementService(
        InterfacePolicyBindingRepository interfacePolicyBindingRepository,
        PolicyTemplateRepository policyTemplateRepository,
        RuntimePolicySnapshotRepository runtimePolicySnapshotRepository,
        InMemoryRateLimitService rateLimitService,
        ObjectMapper objectMapper
    ) {
        this.interfacePolicyBindingRepository = interfacePolicyBindingRepository;
        this.policyTemplateRepository = policyTemplateRepository;
        this.runtimePolicySnapshotRepository = runtimePolicySnapshotRepository;
        this.rateLimitService = rateLimitService;
        this.objectMapper = objectMapper;
    }

    @Transactional
    public ResolvedPolicy resolveAndSnapshot(
        String executionId,
        InterfaceDefinition definition,
        long configTimeoutMillis,
        PolicyExecutionContext context
    ) {
        PolicyTemplate template = resolvePolicyTemplate(definition, context.partnerId());
        validateAuth(template.getAuthType(), context);
        validateAllowedPartner(template, context.partnerId());
        validateRole(template, context.roles());
        validateRateLimit(definition.getInterfaceCode(), context, template.getRateLimitPerMinute());

        long timeoutMillis = template.getTimeoutMillis() > 0 ? template.getTimeoutMillis() : configTimeoutMillis;
        ResolvedPolicy policy = new ResolvedPolicy(
            template.getPolicyName(),
            timeoutMillis,
            Math.max(template.getRetryMaxAttempts(), 0),
            Math.max(template.getRetryIntervalMillis(), 0),
            template.isMaskRequestPayload(),
            template.isMaskResponsePayload()
        );

        runtimePolicySnapshotRepository.save(
            RuntimePolicySnapshot.create(
                executionId,
                definition.getInterfaceCode(),
                context.partnerId(),
                context.clientId(),
                policy.policyName(),
                toSnapshotJson(template, context)
            )
        );
        return policy;
    }

    private PolicyTemplate resolvePolicyTemplate(InterfaceDefinition definition, String partnerId) {
        List<InterfacePolicyBinding> bindings = interfacePolicyBindingRepository
            .findByInterfaceDefinitionAndEnabledTrueOrderByPriorityDesc(definition);
        for (InterfacePolicyBinding binding : bindings) {
            if (!binding.getPolicyTemplate().isEnabled()) {
                continue;
            }
            if (binding.getPartnerId() == null || Objects.equals(binding.getPartnerId(), partnerId)) {
                return binding.getPolicyTemplate();
            }
        }
        return policyTemplateRepository.findByPolicyName(DEFAULT_POLICY_NAME)
            .orElseThrow(() -> new BusinessException(ErrorCode.INVALID_CONFIG, "DEFAULT policy template not found"));
    }

    private void validateAuth(PolicyAuthType authType, PolicyExecutionContext context) {
        switch (authType) {
            case NONE -> {}
            case API_KEY -> requireNotBlank(context.apiKey(), "API key is required by policy");
            case CLIENT_CREDENTIALS -> {
                requireNotBlank(context.clientId(), "Client ID is required by policy");
                requireNotBlank(context.clientSecret(), "Client secret is required by policy");
            }
            case JWT -> {
                requireNotBlank(context.authorizationHeader(), "Authorization header is required by policy");
                if (!context.authorizationHeader().startsWith("Bearer ")) {
                    throw new BusinessException(ErrorCode.UNAUTHORIZED, "Bearer token is required by JWT policy");
                }
            }
            case BASIC -> {
                requireNotBlank(context.authorizationHeader(), "Authorization header is required by policy");
                if (!context.authorizationHeader().startsWith("Basic ")) {
                    throw new BusinessException(ErrorCode.UNAUTHORIZED, "Basic authorization is required by policy");
                }
            }
            case MTLS -> {
                if (!"true".equalsIgnoreCase(context.authorizationHeader())) {
                    throw new BusinessException(ErrorCode.UNAUTHORIZED, "mTLS verification header is required by policy");
                }
            }
        }
    }

    private void validateAllowedPartner(PolicyTemplate template, String partnerId) {
        Set<String> allowed = toSet(template.getAllowedPartnerIdsJson());
        if (allowed.isEmpty()) {
            return;
        }
        if (partnerId == null || !allowed.contains(partnerId)) {
            throw new BusinessException(ErrorCode.FORBIDDEN_ROLE, "Partner is not allowed by policy");
        }
    }

    private void validateRole(PolicyTemplate template, Set<String> roles) {
        Set<String> allowedRoles = toSet(template.getAllowedRolesJson());
        if (allowedRoles.isEmpty()) {
            return;
        }
        boolean matched = roles != null && roles.stream().anyMatch(allowedRoles::contains);
        if (!matched) {
            throw new BusinessException(ErrorCode.FORBIDDEN_ROLE, "Caller role is not allowed by policy");
        }
    }

    private void validateRateLimit(String interfaceCode, PolicyExecutionContext context, int limitPerMinute) {
        String identity = firstNonBlank(context.clientId(), context.remoteIp(), "UNKNOWN");
        String key = interfaceCode + ":" + identity;
        if (!rateLimitService.tryAcquire(key, limitPerMinute)) {
            throw new BusinessException(ErrorCode.RATE_LIMITED, "Rate limit exceeded for policy");
        }
    }

    private void requireNotBlank(String value, String message) {
        if (value == null || value.isBlank()) {
            throw new BusinessException(ErrorCode.UNAUTHORIZED, message);
        }
    }

    private String toSnapshotJson(PolicyTemplate template, PolicyExecutionContext context) {
        try {
            java.util.Map<String, Object> snapshot = new java.util.LinkedHashMap<>();
            snapshot.put("policyName", template.getPolicyName());
            snapshot.put("authType", template.getAuthType());
            snapshot.put("timeoutMillis", template.getTimeoutMillis());
            snapshot.put("retryMaxAttempts", template.getRetryMaxAttempts());
            snapshot.put("retryIntervalMillis", template.getRetryIntervalMillis());
            snapshot.put("rateLimitPerMinute", template.getRateLimitPerMinute());
            snapshot.put("allowedPartnerIdsJson", template.getAllowedPartnerIdsJson());
            snapshot.put("allowedRolesJson", template.getAllowedRolesJson());
            snapshot.put("maskRequestPayload", template.isMaskRequestPayload());
            snapshot.put("maskResponsePayload", template.isMaskResponsePayload());
            snapshot.put("clientId", context.clientId());
            snapshot.put("partnerId", context.partnerId());
            snapshot.put("remoteIp", context.remoteIp());
            return objectMapper.writeValueAsString(snapshot);
        } catch (JsonProcessingException exception) {
            throw new BusinessException(ErrorCode.INTERNAL_ERROR, "Failed to serialize policy snapshot");
        }
    }

    private Set<String> toSet(String json) {
        if (json == null || json.isBlank()) {
            return Set.of();
        }
        try {
            List<String> values = objectMapper.readValue(json, new TypeReference<>() {
            });
            return values.stream().filter(value -> value != null && !value.isBlank()).collect(java.util.stream.Collectors.toSet());
        } catch (JsonProcessingException exception) {
            throw new BusinessException(ErrorCode.INVALID_CONFIG, "Policy JSON configuration is invalid");
        }
    }

    private String firstNonBlank(String... values) {
        for (String value : values) {
            if (value != null && !value.isBlank()) {
                return value;
            }
        }
        return null;
    }
}
