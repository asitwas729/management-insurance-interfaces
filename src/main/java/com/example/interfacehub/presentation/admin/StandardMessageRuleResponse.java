package com.example.interfacehub.presentation.admin;

import com.example.interfacehub.domain.standardmessage.RuleOperator;
import com.example.interfacehub.domain.standardmessage.RuleSeverity;
import com.example.interfacehub.domain.standardmessage.StandardMessageRule;
import java.time.LocalDateTime;

public record StandardMessageRuleResponse(
    String ruleId,
    RuleSeverity severity,
    String xpathExpr,
    RuleOperator operator,
    String expectedValue,
    String message,
    boolean enabled,
    LocalDateTime updatedAt
) {
    public static StandardMessageRuleResponse from(StandardMessageRule rule) {
        return new StandardMessageRuleResponse(
            rule.getRuleId(),
            rule.getSeverity(),
            rule.getXpathExpr(),
            rule.getOperator(),
            rule.getExpectedValue(),
            rule.getMessage(),
            rule.isEnabled(),
            rule.getUpdatedAt()
        );
    }
}

