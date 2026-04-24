package com.example.interfacehub.presentation;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record RegisterRequest(
    @NotBlank
    @Size(min = 3, max = 30)
    @Pattern(regexp = "^[a-zA-Z0-9_]+$", message = "username must be alphanumeric or underscore")
    String username,

    @NotBlank
    @Size(min = 8, max = 72)
    String password
) {
}

