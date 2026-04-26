package com.example.interfacehub.application.admin;

import com.example.interfacehub.application.audit.AuditLogService;
import com.example.interfacehub.domain.audit.AuditAction;
import com.example.interfacehub.domain.interfaceconfig.InterfaceConfigVersion;
import com.example.interfacehub.domain.interfaceconfig.InterfaceDefinition;
import com.example.interfacehub.domain.policy.PolicyTemplate;
import com.example.interfacehub.domain.standard.ErrorCatalog;
import com.example.interfacehub.infrastructure.persistence.ErrorCatalogRepository;
import com.example.interfacehub.infrastructure.persistence.InterfaceConfigVersionRepository;
import com.example.interfacehub.infrastructure.persistence.InterfaceDefinitionRepository;
import com.example.interfacehub.infrastructure.persistence.PolicyTemplateRepository;
import com.example.interfacehub.presentation.admin.ExportPayload;
import com.example.interfacehub.presentation.admin.ExportPayload.ErrorCatalogExport;
import com.example.interfacehub.presentation.admin.ExportPayload.InterfaceConfigVersionExport;
import com.example.interfacehub.presentation.admin.ExportPayload.InterfaceDefinitionExport;
import com.example.interfacehub.presentation.admin.ExportPayload.PolicyTemplateExport;
import com.example.interfacehub.presentation.admin.ImportResult;
import java.util.List;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ConfigImportService {

    private final InterfaceDefinitionRepository definitionRepository;
    private final InterfaceConfigVersionRepository configVersionRepository;
    private final ErrorCatalogRepository errorCatalogRepository;
    private final PolicyTemplateRepository policyTemplateRepository;
    private final AuditLogService auditLogService;
    private final CacheManager cacheManager;

    public ConfigImportService(
        InterfaceDefinitionRepository definitionRepository,
        InterfaceConfigVersionRepository configVersionRepository,
        ErrorCatalogRepository errorCatalogRepository,
        PolicyTemplateRepository policyTemplateRepository,
        AuditLogService auditLogService,
        CacheManager cacheManager
    ) {
        this.definitionRepository = definitionRepository;
        this.configVersionRepository = configVersionRepository;
        this.errorCatalogRepository = errorCatalogRepository;
        this.policyTemplateRepository = policyTemplateRepository;
        this.auditLogService = auditLogService;
        this.cacheManager = cacheManager;
    }

    @Transactional
    public ImportResult importAll(ExportPayload payload, String actor) {
        int created = 0;
        int updated = 0;

        for (var entry : payload.interfaces()) {
            InterfaceDefinitionExport srcDef = entry.definition();

            var existing = definitionRepository.findByInterfaceCode(srcDef.interfaceCode());
            InterfaceDefinition saved = existing.orElseGet(() -> definitionRepository.save(InterfaceDefinition.create(
                srcDef.interfaceCode(),
                srcDef.name(),
                srcDef.protocolType(),
                srcDef.ownerTeam(),
                srcDef.businessCategory(),
                srcDef.externalOrg(),
                srcDef.callDirection(),
                srcDef.slaMillis()
            )));

            if (existing.isEmpty()) {
                created++;
            } else {
                updated++;
            }

            int maxVersion = configVersionRepository.findMaxVersionByInterfaceDefinition(saved);
            List<InterfaceConfigVersionExport> srcConfigs = entry.configVersions();
            for (int index = 0; index < srcConfigs.size(); index++) {
                InterfaceConfigVersionExport src = srcConfigs.get(index);
                InterfaceConfigVersion newVersion = InterfaceConfigVersion.create(
                    saved,
                    maxVersion + index + 1,
                    src.endpoint(),
                    src.authType(),
                    src.headersJson(),
                    src.timeoutMillis(),
                    src.environment(),
                    src.protocolConfigJson(),
                    src.requestSample(),
                    src.responseSample(),
                    src.mappingRuleText(),
                    src.fieldDescriptionText(),
                    src.errorCodeGuideText()
                );

                if (src.sandboxMode()) {
                    newVersion.configureSandbox(true, src.mockHttpStatus(), src.mockResponseBody());
                }

                configVersionRepository.save(newVersion);
            }
        }

        upsertErrorCatalog(payload.errorCatalog());
        upsertPolicyTemplates(payload.policyTemplates());

        evictCaches();

        auditLogService.record(
            actor,
            AuditAction.CONFIG_IMPORTED,
            "GLOBAL",
            "ALL",
            null,
            "created=" + created + ", updated=" + updated
        );

        return new ImportResult(created, updated);
    }

    private void upsertErrorCatalog(List<ErrorCatalogExport> exports) {
        if (exports == null) {
            return;
        }
        for (var src : exports) {
            var existing = errorCatalogRepository.findById(src.code());
            if (existing.isPresent()) {
                existing.get().update(
                    src.domain(),
                    src.severity(),
                    src.httpStatus(),
                    src.retriable(),
                    src.nextAction(),
                    src.description()
                );
                continue;
            }
            ErrorCatalog created = ErrorCatalog.create(
                src.code(),
                src.domain(),
                src.severity(),
                src.httpStatus(),
                src.retriable(),
                src.nextAction(),
                src.description()
            );
            errorCatalogRepository.save(created);
        }
    }

    private void upsertPolicyTemplates(List<PolicyTemplateExport> exports) {
        if (exports == null) {
            return;
        }
        for (var src : exports) {
            var existing = policyTemplateRepository.findByPolicyName(src.policyName());
            if (existing.isPresent()) {
                existing.get().update(
                    src.authType(),
                    src.timeoutMillis(),
                    src.retryMaxAttempts(),
                    src.retryIntervalMillis(),
                    src.rateLimitPerMinute(),
                    src.allowedPartnerIdsJson(),
                    src.maskRequestPayload(),
                    src.maskResponsePayload(),
                    src.allowedRolesJson(),
                    src.enabled()
                );
                continue;
            }
            policyTemplateRepository.save(PolicyTemplate.create(
                src.policyName(),
                src.authType(),
                src.timeoutMillis(),
                src.retryMaxAttempts(),
                src.retryIntervalMillis(),
                src.rateLimitPerMinute(),
                src.allowedPartnerIdsJson(),
                src.maskRequestPayload(),
                src.maskResponsePayload(),
                src.allowedRolesJson()
            ));
        }
    }

    private void evictCaches() {
        for (String name : List.of("interface", "published-config", "error-catalog", "resilience-policy")) {
            Cache cache = cacheManager.getCache(name);
            if (cache != null) {
                cache.clear();
            }
        }
    }
}
