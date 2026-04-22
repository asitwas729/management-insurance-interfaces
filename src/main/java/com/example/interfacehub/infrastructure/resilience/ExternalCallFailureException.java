package com.example.interfacehub.infrastructure.resilience;

public class ExternalCallFailureException extends RuntimeException {

    private final String errorCode;

    public ExternalCallFailureException(String errorCode, String message) {
        super(message);
        this.errorCode = errorCode;
    }

    public String errorCode() {
        return errorCode;
    }
}
