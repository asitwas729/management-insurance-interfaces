package com.example.interfacehub.domain.standardmessage;

import jakarta.persistence.Column;
import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.Lob;
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import org.hibernate.annotations.UpdateTimestamp;

@Entity
@Table(name = "standard_message_schema")
public class StandardMessageSchema {

    @EmbeddedId
    private StandardMessageSchemaId id;

    @Lob
    @Column(name = "xsd_text", nullable = false)
    private String xsdText;

    @Column(nullable = false)
    private boolean enabled;

    @UpdateTimestamp
    @Column(nullable = false)
    private LocalDateTime updatedAt;

    protected StandardMessageSchema() {
    }

    private StandardMessageSchema(StandardMessageSchemaId id, String xsdText, boolean enabled) {
        this.id = id;
        this.xsdText = xsdText;
        this.enabled = enabled;
    }

    public static StandardMessageSchema create(String schemaCode, int version, String xsdText, boolean enabled) {
        return new StandardMessageSchema(new StandardMessageSchemaId(schemaCode, version), xsdText, enabled);
    }

    public void update(String xsdText, boolean enabled) {
        this.xsdText = xsdText;
        this.enabled = enabled;
    }

    public StandardMessageSchemaId getId() {
        return id;
    }

    public String getSchemaCode() {
        return id == null ? null : id.getSchemaCode();
    }

    public int getVersion() {
        return id == null || id.getVersion() == null ? 0 : id.getVersion();
    }

    public String getXsdText() {
        return xsdText;
    }

    public boolean isEnabled() {
        return enabled;
    }

    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }
}

