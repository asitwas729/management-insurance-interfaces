package com.example.interfacehub.presentation.admin;

import com.example.interfacehub.application.standardmessage.StandardValidationResult;

public record StandardMessageValidateResponse(
    boolean valid,
    java.util.List<StandardValidationResult.Violation> errors,
    java.util.List<StandardValidationResult.Violation> warnings
) {
    public static StandardMessageValidateResponse from(StandardValidationResult result) {
        return new StandardMessageValidateResponse(result.valid(), result.errors(), result.warnings());
    }
}

