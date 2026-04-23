package com.example.interfacehub.presentation;

public record LoginResponse(
    String tokenType,
    String accessToken,
    String refreshToken
) {
    public static LoginResponse bearer(String accessToken, String refreshToken) {
        return new LoginResponse("Bearer", accessToken, refreshToken);
    }
}
