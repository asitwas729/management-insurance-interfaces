package com.example.interfacehub.application.admin;

import com.example.interfacehub.application.audit.AuditLogService;
import com.example.interfacehub.domain.audit.AuditAction;
import com.example.interfacehub.infrastructure.persistence.ErrorCatalogRepository;
import com.example.interfacehub.infrastructure.persistence.InterfaceConfigVersionRepository;
import com.example.interfacehub.infrastructure.persistence.InterfaceDefinitionRepository;
import com.example.interfacehub.infrastructure.persistence.PolicyTemplateRepository;
import com.example.interfacehub.presentation.admin.ExportPayload;
import com.example.interfacehub.presentation.admin.ExportPayload.ErrorCatalogExport;
import com.example.interfacehub.presentation.admin.ExportPayload.InterfaceConfigVersionExport;
import com.example.interfacehub.presentation.admin.ExportPayload.InterfaceDefinitionExport;
import com.example.interfacehub.presentation.admin.ExportPayload.InterfaceExportEntry;
import com.example.interfacehub.presentation.admin.ExportPayload.PolicyTemplateExport;
import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.List;
import org.springframework.stereotype.Service;

@Service
public class ConfigExportService {

    private final InterfaceDefinitionRepository definitionRepository;
    private final InterfaceConfigVersionRepository configVersionRepository;
    private final ErrorCatalogRepository errorCatalogRepository;
    private final PolicyTemplateRepository policyTemplateRepository;
    private final AuditLogService auditLogService;

    public ConfigExportService(
        InterfaceDefinitionRepository definitionRepository,
        InterfaceConfigVersionRepository configVersionRepository,
        ErrorCatalogRepository errorCatalogRepository,
        PolicyTemplateRepository policyTemplateRepository,
        AuditLogService auditLogService
    ) {
        this.definitionRepository = definitionRepository;
        this.configVersionRepository = configVersionRepository;
        this.errorCatalogRepository = errorCatalogRepository;
        this.policyTemplateRepository = policyTemplateRepository;
        this.auditLogService = auditLogService;
    }

    public ExportPayload exportAll(String actor) {
        var definitions = definitionRepository.findAll().stream()
            .sorted(Comparator.comparing(d -> d.getInterfaceCode().toLowerCase()))
            .toList();

        List<InterfaceExportEntry> entries = definitions.stream()
            .map(def -> {
                var definitionExport = new InterfaceDefinitionExport(
                    def.getInterfaceCode(),
                    def.getName(),
                    def.getProtocolType(),
                    def.getOwnerTeam(),
                    def.getBusinessCategory(),
                    def.getExternalOrg(),
                    def.getCallDirection(),
                    def.getSlaMillis()
                );

                var configExports = configVersionRepository.findByInterfaceDefinition(def).stream()
                    .sorted(Comparator.comparingInt(c -> c.getVersion() == null ? 0 : c.getVersion()))
                    .map(c -> new InterfaceConfigVersionExport(
                        c.getVersion(),
                        c.getEndpoint(),
                        c.getAuthType(),
                        c.getHeadersJson(),
                        c.getTimeoutMillis(),
                        c.getEnvironment(),
                        c.getProtocolConfigJson(),
                        c.getRequestSample(),
                        c.getResponseSample(),
                        c.getMappingRuleText(),
                        c.getFieldDescriptionText(),
                        c.getErrorCodeGuideText(),
                        c.isSandboxMode(),
                        c.getMockHttpStatus(),
                        c.getMockResponseBody(),
                        c.isPublished()
                    ))
                    .toList();

                return new InterfaceExportEntry(definitionExport, configExports);
            })
            .toList();

        var errorCatalog = errorCatalogRepository.findAll().stream()
            .sorted(Comparator.comparing(e -> e.getCode().toLowerCase()))
            .map(e -> new ErrorCatalogExport(
                e.getCode(),
                e.getDomain(),
                e.getSeverity(),
                e.getHttpStatus(),
                e.isRetriable(),
                e.getNextAction(),
                e.getDescription()
            ))
            .toList();

        var policyTemplates = policyTemplateRepository.findAll().stream()
            .sorted(Comparator.comparing(t -> t.getPolicyName().toLowerCase()))
            .map(t -> new PolicyTemplateExport(
                t.getPolicyName(),
                t.getAuthType(),
                t.getTimeoutMillis(),
                t.getRetryMaxAttempts(),
                t.getRetryIntervalMillis(),
                t.getRateLimitPerMinute(),
                t.getAllowedPartnerIdsJson(),
                t.isMaskRequestPayload(),
                t.isMaskResponsePayload(),
                t.getAllowedRolesJson(),
                t.isEnabled()
            ))
            .toList();

        ExportPayload payload = new ExportPayload(
            LocalDateTime.now(),
            entries,
            errorCatalog,
            policyTemplates
        );

        auditLogService.record(
            actor,
            AuditAction.CONFIG_EXPORTED,
            "GLOBAL",
            "ALL",
            null,
            definitions.size() + " interfaces"
        );

        return payload;
    }
}

