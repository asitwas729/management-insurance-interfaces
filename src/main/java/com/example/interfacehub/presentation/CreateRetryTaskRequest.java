package com.example.interfacehub.presentation;

import jakarta.validation.constraints.NotBlank;

public record CreateRetryTaskRequest(
    @NotBlank String originalExecutionId,
    @NotBlank String requester,
    @NotBlank String reasonCode,
    String reasonDetail
) {
}
