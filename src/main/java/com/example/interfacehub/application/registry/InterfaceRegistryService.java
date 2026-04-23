package com.example.interfacehub.application.registry;

import com.example.interfacehub.application.audit.AuditLogService;
import com.example.interfacehub.common.error.BusinessException;
import com.example.interfacehub.common.error.ErrorCode;
import com.example.interfacehub.domain.interfaceconfig.InterfaceConfigVersion;
import com.example.interfacehub.domain.interfaceconfig.InterfaceDefinition;
import com.example.interfacehub.domain.interfaceconfig.InterfaceStatus;
import com.example.interfacehub.infrastructure.persistence.ExecutionHistoryRepository;
import com.example.interfacehub.infrastructure.persistence.InterfaceConfigVersionRepository;
import com.example.interfacehub.infrastructure.persistence.InterfaceDefinitionRepository;
import com.example.interfacehub.presentation.CreateConfigRequest;
import com.example.interfacehub.presentation.CreateInterfaceRequest;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class InterfaceRegistryService {

    private final InterfaceDefinitionRepository interfaceDefinitionRepository;
    private final InterfaceConfigVersionRepository interfaceConfigVersionRepository;
    private final ExecutionHistoryRepository executionHistoryRepository;
    private final ObjectMapper objectMapper;
    private final AuditLogService auditLogService;

    public InterfaceRegistryService(
        InterfaceDefinitionRepository interfaceDefinitionRepository,
        InterfaceConfigVersionRepository interfaceConfigVersionRepository,
        ExecutionHistoryRepository executionHistoryRepository,
        ObjectMapper objectMapper,
        AuditLogService auditLogService
    ) {
        this.interfaceDefinitionRepository = interfaceDefinitionRepository;
        this.interfaceConfigVersionRepository = interfaceConfigVersionRepository;
        this.executionHistoryRepository = executionHistoryRepository;
        this.objectMapper = objectMapper;
        this.auditLogService = auditLogService;
    }

    @Transactional
    @CacheEvict(cacheNames = {"interface", "published-config"}, allEntries = true)
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
    public List<InterfaceWithStats> findAllInterfacesWithStats() {
        return interfaceDefinitionRepository.findAll().stream()
            .map(definition -> {
                int configCount = interfaceConfigVersionRepository.countByInterfaceDefinition(definition);
                var lastExec = executionHistoryRepository
                    .findFirstByInterfaceCodeOrderByStartedAtDesc(definition.getInterfaceCode())
                    .map(h -> h.getStartedAt())
                    .orElse(null);
                return new InterfaceWithStats(definition, configCount, lastExec);
            })
            .toList();
    }

    public record InterfaceWithStats(
        InterfaceDefinition definition,
        int configCount,
        java.time.LocalDateTime lastExecutedAt
    ) {}

    @Transactional(readOnly = true)
    public List<InterfaceDefinition> findAllInterfaces() {
        return interfaceDefinitionRepository.findAll();
    }

    @Transactional(readOnly = true)
    @Cacheable(cacheNames = "interface", key = "#interfaceCode")
    public InterfaceDefinition findByCode(String interfaceCode) {
        return interfaceDefinitionRepository.findByInterfaceCode(interfaceCode)
            .orElseThrow(() -> new BusinessException(ErrorCode.IF_NOT_FOUND));
    }

    @Transactional
    @CacheEvict(cacheNames = "published-config", key = "#interfaceCode")
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
    @CacheEvict(cacheNames = "published-config", key = "#interfaceCode")
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

    @Transactional
    @CacheEvict(cacheNames = {"interface", "published-config", "maintenance"}, allEntries = true)
    public InterfaceDefinition changeStatus(String interfaceCode, InterfaceStatus newStatus) {
        InterfaceDefinition definition = findByCode(interfaceCode);
        InterfaceStatus oldStatus = definition.getStatus();
        definition.changeStatus(newStatus);
        auditLogService.record(
            "system",
            "CHANGE_STATUS",
            "INTERFACE_DEFINITION",
            interfaceCode,
            "{\"status\":\"" + oldStatus.name() + "\"}",
            "{\"status\":\"" + newStatus.name() + "\"}"
        );
        return definition;
    }

    @Transactional(readOnly = true)
    public Page<InterfaceConfigVersion> findConfigsByCode(String interfaceCode, Pageable pageable) {
        InterfaceDefinition definition = findByCode(interfaceCode);
        return interfaceConfigVersionRepository.findByInterfaceDefinitionOrderByVersionDesc(definition, pageable);
    }

    @Transactional(readOnly = true)
    public InterfaceConfigVersion findConfigById(String interfaceCode, Long configId) {
        InterfaceDefinition definition = findByCode(interfaceCode);
        InterfaceConfigVersion config = interfaceConfigVersionRepository.findById(configId)
            .orElseThrow(() -> new BusinessException(ErrorCode.CONFIG_NOT_FOUND));
        if (!config.getInterfaceDefinition().getId().equals(definition.getId())) {
            throw new BusinessException(ErrorCode.CONFIG_NOT_BELONG_TO_INTERFACE);
        }
        return config;
    }

    @Transactional(readOnly = true)
    public int countConfigs(InterfaceDefinition definition) {
        return interfaceConfigVersionRepository.countByInterfaceDefinition(definition);
    }

    @Transactional(readOnly = true)
    @Cacheable(cacheNames = "published-config", key = "#definition.interfaceCode")
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
