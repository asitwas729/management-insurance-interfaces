package com.example.interfacehub.presentation;

import jakarta.validation.constraints.NotBlank;
import java.util.Map;

public record CreateDlqReplayRequest(
    @NotBlank String requester,
    @NotBlank String reasonCode,
    String reasonDetail,
    Map<String, Object> payloadOverride
) {
}
