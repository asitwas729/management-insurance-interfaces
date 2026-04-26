package com.example.interfacehub.domain.standardmessage;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import java.io.Serializable;
import java.util.Objects;

@Embeddable
public class StandardMessageSchemaId implements Serializable {

    @Column(name = "schema_code", length = 100, nullable = false)
    private String schemaCode;

    @Column(name = "version", nullable = false)
    private Integer version;

    protected StandardMessageSchemaId() {
    }

    public StandardMessageSchemaId(String schemaCode, Integer version) {
        this.schemaCode = schemaCode;
        this.version = version;
    }

    public String getSchemaCode() {
        return schemaCode;
    }

    public Integer getVersion() {
        return version;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        StandardMessageSchemaId that = (StandardMessageSchemaId) o;
        return Objects.equals(schemaCode, that.schemaCode) && Objects.equals(version, that.version);
    }

    @Override
    public int hashCode() {
        return Objects.hash(schemaCode, version);
    }
}

