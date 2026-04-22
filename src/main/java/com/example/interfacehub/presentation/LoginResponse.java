package com.example.interfacehub.presentation;

public record LoginResponse(
    String tokenType,
    String accessToken
) {
    public static LoginResponse bearer(String accessToken) {
        return new LoginResponse("Bearer", accessToken);
    }
}
