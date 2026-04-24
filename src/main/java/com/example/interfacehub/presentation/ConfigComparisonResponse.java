package com.example.interfacehub.presentation;

import java.util.List;

public record ConfigComparisonResponse(
    String interfaceCode,
    ConfigResponse left,
    ConfigResponse right,
    List<FieldDiff> diffs
) {

    public record FieldDiff(
        String field,
        String leftValue,
        String rightValue
    ) {}
}
