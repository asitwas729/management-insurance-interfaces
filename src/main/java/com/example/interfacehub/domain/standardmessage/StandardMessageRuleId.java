package com.example.interfacehub.domain.standardmessage;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import java.io.Serializable;
import java.util.Objects;

@Embeddable
public class StandardMessageRuleId implements Serializable {

    @Column(name = "schema_code", length = 100, nullable = false)
    private String schemaCode;

    @Column(name = "version", nullable = false)
    private Integer version;

    @Column(name = "rule_id", length = 100, nullable = false)
    private String ruleId;

    protected StandardMessageRuleId() {
    }

    public StandardMessageRuleId(String schemaCode, Integer version, String ruleId) {
        this.schemaCode = schemaCode;
        this.version = version;
        this.ruleId = ruleId;
    }

    public String getSchemaCode() {
        return schemaCode;
    }

    public Integer getVersion() {
        return version;
    }

    public String getRuleId() {
        return ruleId;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        StandardMessageRuleId that = (StandardMessageRuleId) o;
        return Objects.equals(schemaCode, that.schemaCode)
            && Objects.equals(version, that.version)
            && Objects.equals(ruleId, that.ruleId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(schemaCode, version, ruleId);
    }
}

