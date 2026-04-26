package com.example.interfacehub.domain.standardmessage;

import jakarta.persistence.Column;
import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import org.hibernate.annotations.UpdateTimestamp;

@Entity
@Table(name = "standard_message_rule")
public class StandardMessageRule {

    @EmbeddedId
    private StandardMessageRuleId id;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private RuleSeverity severity;

    @Column(name = "xpath_expr", nullable = false, length = 500)
    private String xpathExpr;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private RuleOperator operator;

    @Column(name = "expected_value", length = 500)
    private String expectedValue;

    @Column(length = 500)
    private String message;

    @Column(nullable = false)
    private boolean enabled;

    @UpdateTimestamp
    @Column(nullable = false)
    private LocalDateTime updatedAt;

    protected StandardMessageRule() {
    }

    private StandardMessageRule(
        StandardMessageRuleId id,
        RuleSeverity severity,
        String xpathExpr,
        RuleOperator operator,
        String expectedValue,
        String message,
        boolean enabled
    ) {
        this.id = id;
        this.severity = severity;
        this.xpathExpr = xpathExpr;
        this.operator = operator;
        this.expectedValue = expectedValue;
        this.message = message;
        this.enabled = enabled;
    }

    public static StandardMessageRule create(
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
        return new StandardMessageRule(
            new StandardMessageRuleId(schemaCode, version, ruleId),
            severity,
            xpathExpr,
            operator,
            expectedValue,
            message,
            enabled
        );
    }

    public void update(
        RuleSeverity severity,
        String xpathExpr,
        RuleOperator operator,
        String expectedValue,
        String message,
        boolean enabled
    ) {
        this.severity = severity;
        this.xpathExpr = xpathExpr;
        this.operator = operator;
        this.expectedValue = expectedValue;
        this.message = message;
        this.enabled = enabled;
    }

    public StandardMessageRuleId getId() {
        return id;
    }

    public String getSchemaCode() {
        return id == null ? null : id.getSchemaCode();
    }

    public int getVersion() {
        return id == null || id.getVersion() == null ? 0 : id.getVersion();
    }

    public String getRuleId() {
        return id == null ? null : id.getRuleId();
    }

    public RuleSeverity getSeverity() {
        return severity;
    }

    public String getXpathExpr() {
        return xpathExpr;
    }

    public RuleOperator getOperator() {
        return operator;
    }

    public String getExpectedValue() {
        return expectedValue;
    }

    public String getMessage() {
        return message;
    }

    public boolean isEnabled() {
        return enabled;
    }

    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }
}

