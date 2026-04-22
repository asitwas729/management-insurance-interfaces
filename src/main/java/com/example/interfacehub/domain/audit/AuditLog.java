package com.example.interfacehub.domain.audit;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import java.time.LocalDateTime;

@Entity
public class AuditLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 100)
    private String actor;

    @Column(nullable = false, length = 100)
    private String action;

    @Column(nullable = false, length = 100)
    private String targetType;

    @Column(nullable = false, length = 100)
    private String targetId;

    @Column(columnDefinition = "TEXT")
    private String beforeValue;

    @Column(columnDefinition = "TEXT")
    private String afterValue;

    @Column(nullable = false)
    private LocalDateTime createdAt;

    protected AuditLog() {
    }

    private AuditLog(String actor, String action, String targetType, String targetId, String beforeValue, String afterValue) {
        this.actor = actor;
        this.action = action;
        this.targetType = targetType;
        this.targetId = targetId;
        this.beforeValue = beforeValue;
        this.afterValue = afterValue;
        this.createdAt = LocalDateTime.now();
    }

    public static AuditLog record(String actor, String action, String targetType, String targetId, String beforeValue, String afterValue) {
        return new AuditLog(actor, action, targetType, targetId, beforeValue, afterValue);
    }

    public Long getId() {
        return id;
    }

    public String getActor() {
        return actor;
    }

    public String getAction() {
        return action;
    }

    public String getTargetType() {
        return targetType;
    }

    public String getTargetId() {
        return targetId;
    }

    public String getBeforeValue() {
        return beforeValue;
    }

    public String getAfterValue() {
        return afterValue;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }
}
