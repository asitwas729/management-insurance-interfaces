package com.example.interfacehub.domain.standard;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import java.time.LocalDateTime;

@Entity
public class ReprocessPolicy {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 50)
    private String errorCode;

    @Column(nullable = false, length = 30)
    private String mode;

    @Column(nullable = false)
    private int autoMaxAttempts;

    @Column(nullable = false)
    private int backoffSeconds;

    @Column(nullable = false, length = 30)
    private String approvalLevel;

    @Column(nullable = false)
    private boolean enabled;

    @Column(nullable = false)
    private LocalDateTime updatedAt;

    protected ReprocessPolicy() {
    }

    private ReprocessPolicy(String errorCode) {
        this.errorCode = errorCode;
        this.mode = "NONE";
        this.autoMaxAttempts = 0;
        this.backoffSeconds = 0;
        this.approvalLevel = "NONE";
        this.enabled = true;
        this.updatedAt = LocalDateTime.now();
    }

    public static ReprocessPolicy createDefault(String errorCode) {
        return new ReprocessPolicy(errorCode);
    }

    public void update(String mode, int autoMaxAttempts, int backoffSeconds, String approvalLevel, boolean enabled) {
        this.mode = mode;
        this.autoMaxAttempts = autoMaxAttempts;
        this.backoffSeconds = backoffSeconds;
        this.approvalLevel = approvalLevel;
        this.enabled = enabled;
        this.updatedAt = LocalDateTime.now();
    }

    public Long getId() {
        return id;
    }

    public String getErrorCode() {
        return errorCode;
    }

    public String getMode() {
        return mode;
    }

    public int getAutoMaxAttempts() {
        return autoMaxAttempts;
    }

    public int getBackoffSeconds() {
        return backoffSeconds;
    }

    public String getApprovalLevel() {
        return approvalLevel;
    }

    public boolean isEnabled() {
        return enabled;
    }
}
