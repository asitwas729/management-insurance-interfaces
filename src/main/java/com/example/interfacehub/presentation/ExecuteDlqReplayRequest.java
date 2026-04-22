package com.example.interfacehub.presentation;

import jakarta.validation.constraints.NotBlank;

public record ExecuteDlqReplayRequest(
    @NotBlank String executor
) {
}
