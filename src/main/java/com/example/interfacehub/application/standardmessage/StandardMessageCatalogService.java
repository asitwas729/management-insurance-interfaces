package com.example.interfacehub.application.standardmessage;

import com.example.interfacehub.common.error.BusinessException;
import com.example.interfacehub.common.error.ErrorCode;
import com.example.interfacehub.domain.standardmessage.StandardMessageRule;
import com.example.interfacehub.domain.standardmessage.StandardMessageSchema;
import com.example.interfacehub.domain.standardmessage.StandardMessageSchemaId;
import com.example.interfacehub.infrastructure.persistence.StandardMessageRuleRepository;
import com.example.interfacehub.infrastructure.persistence.StandardMessageSchemaRepository;
import java.util.List;
import java.util.Optional;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class StandardMessageCatalogService {

    private final StandardMessageSchemaRepository schemaRepository;
    private final StandardMessageRuleRepository ruleRepository;

    public StandardMessageCatalogService(
        StandardMessageSchemaRepository schemaRepository,
        StandardMessageRuleRepository ruleRepository
    ) {
        this.schemaRepository = schemaRepository;
        this.ruleRepository = ruleRepository;
    }

    @Transactional(readOnly = true)
    @Cacheable(cacheNames = "standard-message-schema", key = "#schemaCode + ':' + #version", unless = "#result == null")
    public StandardMessageSchema findEnabledSchema(String schemaCode, int version) {
        return schemaRepository.findByIdSchemaCodeAndIdVersionAndEnabledTrue(schemaCode, version).orElse(null);
    }

    @Transactional(readOnly = true)
    @Cacheable(cacheNames = "standard-message-rules", key = "#schemaCode + ':' + #version")
    public List<StandardMessageRule> findEnabledRules(String schemaCode, int version) {
        return ruleRepository.findByIdSchemaCodeAndIdVersionAndEnabledTrueOrderByIdRuleIdAsc(schemaCode, version);
    }

    @Transactional(readOnly = true)
    public Optional<StandardMessageSchema> findSchema(String schemaCode, int version) {
        return schemaRepository.findById(new StandardMessageSchemaId(schemaCode, version));
    }

    @Transactional(readOnly = true)
    public List<StandardMessageRule> findRules(String schemaCode, int version) {
        return ruleRepository.findByIdSchemaCodeAndIdVersionAndEnabledTrueOrderByIdRuleIdAsc(schemaCode, version);
    }

    @Transactional
    @CacheEvict(cacheNames = {"standard-message-schema", "standard-message-rules"}, key = "#schemaCode + ':' + #version")
    public StandardMessageSchema upsertSchema(String schemaCode, int version, String xsdText, boolean enabled) {
        if (schemaCode == null || schemaCode.isBlank() || version <= 0) {
            throw new BusinessException(ErrorCode.INVALID_REQUEST, "schemaCode/version is required");
        }
        if (xsdText == null || xsdText.isBlank()) {
            throw new BusinessException(ErrorCode.INVALID_REQUEST, "xsdText is required");
        }
        StandardMessageSchema schema = schemaRepository.findById(new StandardMessageSchemaId(schemaCode, version))
            .map(existing -> {
                existing.update(xsdText, enabled);
                return existing;
            })
            .orElseGet(() -> StandardMessageSchema.create(schemaCode, version, xsdText, enabled));
        return schemaRepository.save(schema);
    }
}

