package com.example.interfacehub.presentation;

import jakarta.validation.constraints.NotBlank;

public record RejectDlqReplayRequest(
    @NotBlank String approver,
    @NotBlank String reason
) {
}
