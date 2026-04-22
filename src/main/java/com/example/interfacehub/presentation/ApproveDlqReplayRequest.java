package com.example.interfacehub.presentation;

import jakarta.validation.constraints.NotBlank;

public record ApproveDlqReplayRequest(
    @NotBlank String approver
) {
}
