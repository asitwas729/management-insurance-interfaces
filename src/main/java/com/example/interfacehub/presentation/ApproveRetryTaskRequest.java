package com.example.interfacehub.presentation;

import jakarta.validation.constraints.NotBlank;

public record ApproveRetryTaskRequest(@NotBlank String approver) {
}
