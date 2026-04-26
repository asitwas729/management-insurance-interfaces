package com.example.interfacehub.presentation.admin;

import com.example.interfacehub.domain.standardmessage.RuleOperator;
import com.example.interfacehub.domain.standardmessage.RuleSeverity;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record UpsertStandardMessageRuleRequest(
    @NotNull RuleSeverity severity,
    @NotBlank String xpathExpr,
    @NotNull RuleOperator operator,
    String expectedValue,
    String message,
    boolean enabled
) {
}

