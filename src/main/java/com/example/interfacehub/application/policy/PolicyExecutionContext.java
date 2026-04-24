package com.example.interfacehub.application.policy;

import java.util.Set;

public record PolicyExecutionContext(
    String clientId,
    String clientSecret,
    String apiKey,
    String authorizationHeader,
    String partnerId,
    String remoteIp,
    Set<String> roles
) {
    public static PolicyExecutionContext system(String partnerId) {
        return new PolicyExecutionContext(
            "SYSTEM",
            null,
            null,
            null,
            partnerId,
            "127.0.0.1",
            Set.of("ROLE_ADMIN")
        );
    }
}
