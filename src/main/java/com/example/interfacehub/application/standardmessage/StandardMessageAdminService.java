package com.example.interfacehub.application.standardmessage;

import com.example.interfacehub.common.error.BusinessException;
import com.example.interfacehub.common.error.ErrorCode;
import com.example.interfacehub.domain.standardmessage.RuleOperator;
import com.example.interfacehub.domain.standardmessage.RuleSeverity;
import com.example.interfacehub.domain.standardmessage.StandardMessageRule;
import com.example.interfacehub.domain.standardmessage.StandardMessageRuleId;
import com.example.interfacehub.domain.standardmessage.StandardMessageSchema;
import com.example.interfacehub.domain.standardmessage.StandardMessageSchemaId;
import com.example.interfacehub.infrastructure.persistence.StandardMessageRuleRepository;
import com.example.interfacehub.infrastructure.persistence.StandardMessageSchemaRepository;
import java.util.List;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class StandardMessageAdminService {

    private final StandardMessageCatalogService catalogService;
    private final StandardMessageSchemaRepository schemaRepository;
    private final StandardMessageRuleRepository ruleRepository;
    private final StandardMessageValidationService validationService;

    public StandardMessageAdminService(
        StandardMessageCatalogService catalogService,
        StandardMessageSchemaRepository schemaRepository,
        StandardMessageRuleRepository ruleRepository,
        StandardMessageValidationService validationService
    ) {
        this.catalogService = catalogService;
        this.schemaRepository = schemaRepository;
        this.ruleRepository = ruleRepository;
        this.validationService = validationService;
    }

    @Transactional
    public StandardMessageSchema upsertSchema(String schemaCode, int version, String xsdText, boolean enabled) {
        StandardMessageSchema saved = catalogService.upsertSchema(schemaCode, version, xsdText, enabled);
        validationService.invalidateSchemaCache(schemaCode, version);
        return saved;
    }

    @Transactional(readOnly = true)
    public StandardMessageSchema getSchema(String schemaCode, int version) {
        return schemaRepository.findById(new StandardMessageSchemaId(schemaCode, version))
            .orElseThrow(() -> new BusinessException(ErrorCode.IF_NOT_FOUND, "Standard schema not found"));
    }

    @Transactional(readOnly = true)
    public List<StandardMessageRule> listRules(String schemaCode, int version) {
        return ruleRepository.findByIdSchemaCodeAndIdVersionOrderByIdRuleIdAsc(schemaCode, version);
    }

    @Transactional
    @CacheEvict(cacheNames = "standard-message-rules", key = "#schemaCode + ':' + #version")
    public StandardMessageRule upsertRule(
        String schemaCode,
        int version,
        String ruleId,
        RuleSeverity severity,
        String xpathExpr,
        RuleOperator operator,
        String expectedValue,
        String message,
        boolean enabled
    ) {
        if (schemaCode == null || schemaCode.isBlank() || version <= 0) {
            throw new BusinessException(ErrorCode.INVALID_REQUEST, "schemaCode/version is required");
        }
        if (ruleId == null || ruleId.isBlank()) {
            throw new BusinessException(ErrorCode.INVALID_REQUEST, "ruleId is required");
        }
        if (xpathExpr == null || xpathExpr.isBlank()) {
            throw new BusinessException(ErrorCode.INVALID_REQUEST, "xpathExpr is required");
        }
        if (operator == null) {
            throw new BusinessException(ErrorCode.INVALID_REQUEST, "operator is required");
        }
        schemaRepository.findById(new StandardMessageSchemaId(schemaCode, version))
            .orElseThrow(() -> new BusinessException(ErrorCode.IF_NOT_FOUND, "Standard schema not found"));

        StandardMessageRule rule = ruleRepository.findById(new StandardMessageRuleId(schemaCode, version, ruleId))
            .map(existing -> {
                existing.update(
                    severity == null ? RuleSeverity.ERROR : severity,
                    xpathExpr,
                    operator,
                    expectedValue,
                    message,
                    enabled
                );
                return existing;
            })
            .orElseGet(() -> StandardMessageRule.create(
                schemaCode,
                version,
                ruleId,
                severity == null ? RuleSeverity.ERROR : severity,
                xpathExpr,
                operator,
                expectedValue,
                message,
                enabled
            ));

        return ruleRepository.save(rule);
    }
}

