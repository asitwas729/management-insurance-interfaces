package com.example.interfacehub.application.fixedlength;

public record FixedLengthConfig(
    boolean enforceRequest,
    boolean enforceResponse,
    FixedLengthSchema requestSchema,
    FixedLengthSchema responseSchema
) {
    public boolean enabled() {
        return (enforceRequest && requestSchema != null) || (enforceResponse && responseSchema != null);
    }
}

