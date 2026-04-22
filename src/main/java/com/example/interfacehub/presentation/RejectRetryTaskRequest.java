package com.example.interfacehub.presentation;

import jakarta.validation.constraints.NotBlank;

public record RejectRetryTaskRequest(
    @NotBlank String approver,
    @NotBlank String reason
) {
}
