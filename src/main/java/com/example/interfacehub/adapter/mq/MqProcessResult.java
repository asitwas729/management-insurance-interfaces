package com.example.interfacehub.adapter.mq;

public record MqProcessResult(
    boolean success,
    String responsePayload,
    String errorMessage
) {
    public static MqProcessResult success(String responsePayload) {
        return new MqProcessResult(true, responsePayload, null);
    }

    public static MqProcessResult failure(String errorMessage) {
        return new MqProcessResult(false, null, errorMessage);
    }
}
