package com.example.interfacehub.presentation;

import com.example.interfacehub.domain.standard.ReprocessPolicy;

public record ReprocessPolicyResponse(
    Long id,
    String errorCode,
    String mode,
    int autoMaxAttempts,
    int backoffSeconds,
    String approvalLevel,
    boolean enabled
) {
    public static ReprocessPolicyResponse from(ReprocessPolicy policy) {
        return new ReprocessPolicyResponse(
            policy.getId(),
            policy.getErrorCode(),
            policy.getMode(),
            policy.getAutoMaxAttempts(),
            policy.getBackoffSeconds(),
            policy.getApprovalLevel(),
            policy.isEnabled()
        );
    }
}
