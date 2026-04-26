package com.example.interfacehub.presentation.admin;

import com.example.interfacehub.domain.standardmessage.StandardMessageSchema;
import java.time.LocalDateTime;

public record StandardMessageSchemaResponse(
    String schemaCode,
    int version,
    String xsdText,
    boolean enabled,
    LocalDateTime updatedAt
) {
    public static StandardMessageSchemaResponse from(StandardMessageSchema schema) {
        return new StandardMessageSchemaResponse(
            schema.getSchemaCode(),
            schema.getVersion(),
            schema.getXsdText(),
            schema.isEnabled(),
            schema.getUpdatedAt()
        );
    }
}

