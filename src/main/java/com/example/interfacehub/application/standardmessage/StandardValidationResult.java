package com.example.interfacehub.application.standardmessage;

import java.util.List;

public record StandardValidationResult(
    boolean valid,
    List<Violation> errors,
    List<Violation> warnings
) {
    public record Violation(String ruleId, String message) {
    }
}

