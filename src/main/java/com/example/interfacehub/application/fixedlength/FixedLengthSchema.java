package com.example.interfacehub.application.fixedlength;

import java.util.List;

public record FixedLengthSchema(
    String charset,
    List<FixedLengthFieldSpec> fields
) {
    public int totalLength() {
        return (fields == null ? List.<FixedLengthFieldSpec>of() : fields).stream()
            .mapToInt(FixedLengthFieldSpec::length)
            .sum();
    }
}

