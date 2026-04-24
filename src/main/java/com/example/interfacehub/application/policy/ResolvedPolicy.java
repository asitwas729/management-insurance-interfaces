package com.example.interfacehub.application.policy;

public record ResolvedPolicy(
    String policyName,
    long timeoutMillis,
    int retryMaxAttempts,
    long retryIntervalMillis,
    boolean maskRequestPayload,
    boolean maskResponsePayload
) {
}
