package com.example.interfacehub.domain.policy;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import java.time.LocalDateTime;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

@Entity
public class PolicyTemplate {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 100)
    private String policyName;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private PolicyAuthType authType;

    @Column(nullable = false)
    private Long timeoutMillis;

    @Column(nullable = false)
    private Integer retryMaxAttempts;

    @Column(nullable = false)
    private Long retryIntervalMillis;

    @Column(nullable = false)
    private Integer rateLimitPerMinute;

    @Column(columnDefinition = "TEXT")
    private String allowedPartnerIdsJson;

    @Column(nullable = false)
    private boolean maskRequestPayload;

    @Column(nullable = false)
    private boolean maskResponsePayload;

    @Column(columnDefinition = "TEXT")
    private String allowedRolesJson;

    @Column(nullable = false)
    private boolean enabled;

    @CreationTimestamp
    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(nullable = false)
    private LocalDateTime updatedAt;

    protected PolicyTemplate() {
    }

    private PolicyTemplate(
        String policyName,
        PolicyAuthType authType,
        Long timeoutMillis,
        Integer retryMaxAttempts,
        Long retryIntervalMillis,
        Integer rateLimitPerMinute,
        String allowedPartnerIdsJson,
        boolean maskRequestPayload,
        boolean maskResponsePayload,
        String allowedRolesJson
    ) {
        this.policyName = policyName;
        this.authType = authType;
        this.timeoutMillis = timeoutMillis;
        this.retryMaxAttempts = retryMaxAttempts;
        this.retryIntervalMillis = retryIntervalMillis;
        this.rateLimitPerMinute = rateLimitPerMinute;
        this.allowedPartnerIdsJson = allowedPartnerIdsJson;
        this.maskRequestPayload = maskRequestPayload;
        this.maskResponsePayload = maskResponsePayload;
        this.allowedRolesJson = allowedRolesJson;
        this.enabled = true;
    }

    public static PolicyTemplate create(
        String policyName,
        PolicyAuthType authType,
        Long timeoutMillis,
        Integer retryMaxAttempts,
        Long retryIntervalMillis,
        Integer rateLimitPerMinute,
        String allowedPartnerIdsJson,
        boolean maskRequestPayload,
        boolean maskResponsePayload,
        String allowedRolesJson
    ) {
        return new PolicyTemplate(
            policyName,
            authType,
            timeoutMillis,
            retryMaxAttempts,
            retryIntervalMillis,
            rateLimitPerMinute,
            allowedPartnerIdsJson,
            maskRequestPayload,
            maskResponsePayload,
            allowedRolesJson
        );
    }

    public void update(
        PolicyAuthType authType,
        Long timeoutMillis,
        Integer retryMaxAttempts,
        Long retryIntervalMillis,
        Integer rateLimitPerMinute,
        String allowedPartnerIdsJson,
        boolean maskRequestPayload,
        boolean maskResponsePayload,
        String allowedRolesJson,
        boolean enabled
    ) {
        this.authType = authType;
        this.timeoutMillis = timeoutMillis;
        this.retryMaxAttempts = retryMaxAttempts;
        this.retryIntervalMillis = retryIntervalMillis;
        this.rateLimitPerMinute = rateLimitPerMinute;
        this.allowedPartnerIdsJson = allowedPartnerIdsJson;
        this.maskRequestPayload = maskRequestPayload;
        this.maskResponsePayload = maskResponsePayload;
        this.allowedRolesJson = allowedRolesJson;
        this.enabled = enabled;
    }

    public Long getId() {
        return id;
    }

    public String getPolicyName() {
        return policyName;
    }

    public PolicyAuthType getAuthType() {
        return authType;
    }

    public Long getTimeoutMillis() {
        return timeoutMillis;
    }

    public Integer getRetryMaxAttempts() {
        return retryMaxAttempts;
    }

    public Long getRetryIntervalMillis() {
        return retryIntervalMillis;
    }

    public Integer getRateLimitPerMinute() {
        return rateLimitPerMinute;
    }

    public String getAllowedPartnerIdsJson() {
        return allowedPartnerIdsJson;
    }

    public boolean isMaskRequestPayload() {
        return maskRequestPayload;
    }

    public boolean isMaskResponsePayload() {
        return maskResponsePayload;
    }

    public String getAllowedRolesJson() {
        return allowedRolesJson;
    }

    public boolean isEnabled() {
        return enabled;
    }
}
