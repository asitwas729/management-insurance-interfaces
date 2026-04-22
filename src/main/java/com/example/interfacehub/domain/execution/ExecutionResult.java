package com.example.interfacehub.domain.execution;

public record ExecutionResult(
    boolean success,
    String responsePayload,
    String errorCode,
    String errorMessage,
    long latencyMillis
) {
    public static ExecutionResult success(String responsePayload, long latencyMillis) {
        return new ExecutionResult(true, responsePayload, null, null, latencyMillis);
    }

    public static ExecutionResult failure(String errorCode, String errorMessage, long latencyMillis) {
        return new ExecutionResult(false, null, errorCode, errorMessage, latencyMillis);
    }
}
