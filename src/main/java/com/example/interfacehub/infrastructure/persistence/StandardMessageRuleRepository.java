package com.example.interfacehub.infrastructure.persistence;

import com.example.interfacehub.domain.standardmessage.StandardMessageRule;
import com.example.interfacehub.domain.standardmessage.StandardMessageRuleId;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface StandardMessageRuleRepository extends JpaRepository<StandardMessageRule, StandardMessageRuleId> {

    List<StandardMessageRule> findByIdSchemaCodeAndIdVersionAndEnabledTrueOrderByIdRuleIdAsc(String schemaCode, Integer version);

    List<StandardMessageRule> findByIdSchemaCodeAndIdVersionOrderByIdRuleIdAsc(String schemaCode, Integer version);
}
