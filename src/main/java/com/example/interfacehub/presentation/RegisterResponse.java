package com.example.interfacehub.presentation;

import java.time.LocalDateTime;

public record RegisterResponse(
    String username,
    String roles,
    LocalDateTime createdAt
) {
}

