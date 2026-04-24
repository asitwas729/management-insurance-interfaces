package com.example.interfacehub.application.policy;

import com.example.interfacehub.application.audit.AuditLogService;
import com.example.interfacehub.application.registry.InterfaceRegistryService;
import com.example.interfacehub.common.error.BusinessException;
import com.example.interfacehub.common.error.ErrorCode;
import com.example.interfacehub.domain.policy.InterfacePolicyBinding;
import com.example.interfacehub.domain.policy.PolicyTemplate;
import com.example.interfacehub.infrastructure.persistence.InterfacePolicyBindingRepository;
import com.example.interfacehub.infrastructure.persistence.PolicyTemplateRepository;
import com.example.interfacehub.presentation.BindInterfacePolicyRequest;
import com.example.interfacehub.presentation.CreatePolicyTemplateRequest;
import com.example.interfacehub.presentation.UpdatePolicyTemplateRequest;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class PolicyManagementService {

    private final PolicyTemplateRepository policyTemplateRepository;
    private final InterfacePolicyBindingRepository interfacePolicyBindingRepository;
    private final InterfaceRegistryService interfaceRegistryService;
    private final ObjectMapper objectMapper;
    private final AuditLogService auditLogService;

    public PolicyManagementService(
        PolicyTemplateRepository policyTemplateRepository,
        InterfacePolicyBindingRepository interfacePolicyBindingRepository,
        InterfaceRegistryService interfaceRegistryService,
        ObjectMapper objectMapper,
        AuditLogService auditLogService
    ) {
        this.policyTemplateRepository = policyTemplateRepository;
        this.interfacePolicyBindingRepository = interfacePolicyBindingRepository;
        this.interfaceRegistryService = interfaceRegistryService;
        this.objectMapper = objectMapper;
        this.auditLogService = auditLogService;
    }

    @Transactional
    public PolicyTemplate createTemplate(CreatePolicyTemplateRequest request, String actor) {
        if (policyTemplateRepository.findByPolicyName(request.policyName()).isPresent()) {
            throw new BusinessException(ErrorCode.INVALID_REQUEST, "policyName already exists");
        }
        PolicyTemplate template = PolicyTemplate.create(
            request.policyName(),
            request.authType(),
            request.timeoutMillis(),
            request.retryMaxAttempts(),
            request.retryIntervalMillis(),
            request.rateLimitPerMinute(),
            toJsonArray(request.allowedPartnerIds()),
            request.maskRequestPayload(),
            request.maskResponsePayload(),
            toJsonArray(request.allowedRoles())
        );
        PolicyTemplate saved = policyTemplateRepository.save(template);
        auditLogService.record(
            actor,
            "CREATE_POLICY_TEMPLATE",
            "POLICY_TEMPLATE",
            saved.getPolicyName(),
            null,
            toAuditJson(saved)
        );
        return saved;
    }

    @Transactional
    public PolicyTemplate updateTemplate(String policyName, UpdatePolicyTemplateRequest request, String actor) {
        PolicyTemplate template = policyTemplateRepository.findByPolicyName(policyName)
            .orElseThrow(() -> new BusinessException(ErrorCode.IF_NOT_FOUND, "policy template not found"));
        String before = toAuditJson(template);
        template.update(
            request.authType(),
            request.timeoutMillis(),
            request.retryMaxAttempts(),
            request.retryIntervalMillis(),
            request.rateLimitPerMinute(),
            toJsonArray(request.allowedPartnerIds()),
            request.maskRequestPayload(),
            request.maskResponsePayload(),
            toJsonArray(request.allowedRoles()),
            request.enabled()
        );
        auditLogService.record(
            actor,
            "UPDATE_POLICY_TEMPLATE",
            "POLICY_TEMPLATE",
            template.getPolicyName(),
            before,
            toAuditJson(template)
        );
        return template;
    }

    @Transactional(readOnly = true)
    public List<PolicyTemplate> findTemplates() {
        return policyTemplateRepository.findAll();
    }

    @Transactional
    public InterfacePolicyBinding bindPolicy(
        String interfaceCode,
        BindInterfacePolicyRequest request,
        String actor
    ) {
        var definition = interfaceRegistryService.findByCode(interfaceCode);
        var template = policyTemplateRepository.findByPolicyName(request.policyName())
            .orElseThrow(() -> new BusinessException(ErrorCode.IF_NOT_FOUND, "policy template not found"));
        InterfacePolicyBinding binding = InterfacePolicyBinding.create(
            definition,
            template,
            request.partnerId(),
            request.priority() == null ? 100 : request.priority()
        );
        InterfacePolicyBinding saved = interfacePolicyBindingRepository.save(binding);
        auditLogService.record(
            actor,
            "BIND_INTERFACE_POLICY",
            "INTERFACE_POLICY_BINDING",
            String.valueOf(saved.getId()),
            null,
            "{\"interfaceCode\":\"" + interfaceCode + "\",\"policyName\":\"" + template.getPolicyName() + "\"}"
        );
        return saved;
    }

    @Transactional(readOnly = true)
    public List<InterfacePolicyBinding> findBindings(String interfaceCode) {
        var definition = interfaceRegistryService.findByCode(interfaceCode);
        return interfacePolicyBindingRepository.findByInterfaceDefinitionAndEnabledTrueOrderByPriorityDesc(definition);
    }

    private String toJsonArray(Object value) {
        try {
            return objectMapper.writeValueAsString(value == null ? List.of() : value);
        } catch (JsonProcessingException exception) {
            throw new BusinessException(ErrorCode.INVALID_REQUEST, "Invalid JSON value");
        }
    }

    private String toAuditJson(PolicyTemplate template) {
        try {
            return objectMapper.writeValueAsString(
                java.util.Map.of(
                    "policyName", template.getPolicyName(),
                    "authType", template.getAuthType(),
                    "timeoutMillis", template.getTimeoutMillis(),
                    "retryMaxAttempts", template.getRetryMaxAttempts(),
                    "retryIntervalMillis", template.getRetryIntervalMillis(),
                    "rateLimitPerMinute", template.getRateLimitPerMinute(),
                    "enabled", template.isEnabled()
                )
            );
        } catch (JsonProcessingException exception) {
            return "{}";
        }
    }
}
