package com.example.interfacehub.application.registry;

import com.example.interfacehub.application.audit.AuditLogService;
import com.example.interfacehub.common.error.BusinessException;
import com.example.interfacehub.common.error.ErrorCode;
import com.example.interfacehub.domain.interfaceconfig.InterfaceConfigVersion;
import com.example.interfacehub.domain.interfaceconfig.InterfaceDefinition;
import com.example.interfacehub.infrastructure.persistence.InterfaceConfigVersionRepository;
import com.example.interfacehub.infrastructure.persistence.InterfaceDefinitionRepository;
import com.example.interfacehub.presentation.CreateConfigRequest;
import com.example.interfacehub.presentation.CreateInterfaceRequest;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class InterfaceRegistryService {

    private final InterfaceDefinitionRepository interfaceDefinitionRepository;
    private final InterfaceConfigVersionRepository interfaceConfigVersionRepository;
    private final ObjectMapper objectMapper;
    private final AuditLogService auditLogService;

    public InterfaceRegistryService(
        InterfaceDefinitionRepository interfaceDefinitionRepository,
        InterfaceConfigVersionRepository interfaceConfigVersionRepository,
        ObjectMapper objectMapper,
        AuditLogService auditLogService
    ) {
        this.interfaceDefinitionRepository = interfaceDefinitionRepository;
        this.interfaceConfigVersionRepository = interfaceConfigVersionRepository;
        this.objectMapper = objectMapper;
        this.auditLogService = auditLogService;
    }

    @Transactional
    public InterfaceDefinition createInterface(CreateInterfaceRequest request) {
        if (interfaceDefinitionRepository.existsByInterfaceCode(request.interfaceCode())) {
            throw new BusinessException(ErrorCode.DUPLICATE_INTERFACE_CODE);
        }

        InterfaceDefinition definition = InterfaceDefinition.create(
            request.interfaceCode(),
            request.name(),
            request.protocolType(),
            request.ownerTeam(),
            resolveExternalOrg(request.externalOrg()),
            request.slaMillis()
        );
        return interfaceDefinitionRepository.save(definition);
    }

    @Transactional(readOnly = true)
    public List<InterfaceDefinition> findAllInterfaces() {
        return interfaceDefinitionRepository.findAll();
    }

    @Transactional(readOnly = true)
    public InterfaceDefinition findByCode(String interfaceCode) {
        return interfaceDefinitionRepository.findByInterfaceCode(interfaceCode)
            .orElseThrow(() -> new BusinessException(ErrorCode.IF_NOT_FOUND));
    }

    @Transactional
    public InterfaceConfigVersion createConfig(String interfaceCode, CreateConfigRequest request) {
        InterfaceDefinition definition = findByCode(interfaceCode);
        int nextVersion = interfaceConfigVersionRepository.findMaxVersionByInterfaceDefinition(definition) + 1;

        InterfaceConfigVersion config = InterfaceConfigVersion.create(
            definition,
            nextVersion,
            request.endpoint(),
            request.authType(),
            toJson(request.headers()),
            request.timeoutMillis()
        );

        if (request.sandboxMode()) {
            config.configureSandbox(true, request.mockHttpStatus(), request.mockResponseBody());
        }

        return interfaceConfigVersionRepository.save(config);
    }

    @Transactional
    public InterfaceConfigVersion publishConfig(String interfaceCode, Long configId) {
        return publishConfig(interfaceCode, configId, "system");
    }

    @Transactional
    public InterfaceConfigVersion publishConfig(String interfaceCode, Long configId, String actor) {
        InterfaceDefinition definition = findByCode(interfaceCode);
        InterfaceConfigVersion target = interfaceConfigVersionRepository.findById(configId)
            .orElseThrow(() -> new BusinessException(ErrorCode.CONFIG_NOT_FOUND));

        if (!target.getInterfaceDefinition().getId().equals(definition.getId())) {
            throw new BusinessException(ErrorCode.CONFIG_NOT_BELONG_TO_INTERFACE);
        }

        interfaceConfigVersionRepository.findByInterfaceDefinition(definition)
            .forEach(InterfaceConfigVersion::unpublish);
        target.publish();

        auditLogService.record(
            actor,
            "PUBLISH_CONFIG",
            "INTERFACE_CONFIG_VERSION",
            String.valueOf(target.getId()),
            "{\"published\":false}",
            "{\"published\":true}"
        );
        return target;
    }

    @Transactional(readOnly = true)
    public InterfaceConfigVersion findPublishedConfig(InterfaceDefinition definition) {
        return interfaceConfigVersionRepository.findByInterfaceDefinitionAndPublishedTrue(definition)
            .orElseThrow(() -> new BusinessException(ErrorCode.CONFIG_NOT_FOUND));
    }

    private String toJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value == null ? java.util.Map.of() : value);
        } catch (JsonProcessingException exception) {
            throw new BusinessException(ErrorCode.INVALID_REQUEST, "headers must be valid JSON object");
        }
    }

    private String resolveExternalOrg(String externalOrg) {
        if (externalOrg == null || externalOrg.isBlank()) {
            return "UNKNOWN";
        }
        return externalOrg.trim().toUpperCase();
    }
}
