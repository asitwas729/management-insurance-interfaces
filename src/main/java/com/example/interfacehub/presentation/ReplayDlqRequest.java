package com.example.interfacehub.presentation;

import java.util.Map;

public record ReplayDlqRequest(
    Map<String, Object> payloadOverride
) {
}
