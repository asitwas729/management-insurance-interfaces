package com.example.interfacehub.presentation.admin;

import jakarta.validation.constraints.NotBlank;

public record UpsertStandardMessageSchemaRequest(
    @NotBlank String xsdText,
    boolean enabled
) {
}

