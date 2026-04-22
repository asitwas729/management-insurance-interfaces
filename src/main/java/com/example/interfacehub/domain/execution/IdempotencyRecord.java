package com.example.interfacehub.domain.execution;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import java.time.LocalDateTime;

@Entity
public class IdempotencyRecord {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 200)
    private String idempotencyKey;

    @Column(nullable = false, length = 100)
    private String interfaceCode;

    @Column(nullable = false, length = 100)
    private String executionId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private IdempotencyStatus status;

    @Column(nullable = false)
    private LocalDateTime createdAt;

    @Column(nullable = false)
    private LocalDateTime updatedAt;

    protected IdempotencyRecord() {
    }

    private IdempotencyRecord(String idempotencyKey, String interfaceCode, String executionId) {
        this.idempotencyKey = idempotencyKey;
        this.interfaceCode = interfaceCode;
        this.executionId = executionId;
        this.status = IdempotencyStatus.RESERVED;
        this.createdAt = LocalDateTime.now();
        this.updatedAt = this.createdAt;
    }

    public static IdempotencyRecord reserve(String idempotencyKey, String interfaceCode, String executionId) {
        return new IdempotencyRecord(idempotencyKey, interfaceCode, executionId);
    }

    public void markSuccess() {
        this.status = IdempotencyStatus.SUCCESS;
        this.updatedAt = LocalDateTime.now();
    }

    public void markFailed() {
        this.status = IdempotencyStatus.FAILED;
        this.updatedAt = LocalDateTime.now();
    }

    public Long getId() {
        return id;
    }

    public String getIdempotencyKey() {
        return idempotencyKey;
    }

    public String getInterfaceCode() {
        return interfaceCode;
    }

    public String getExecutionId() {
        return executionId;
    }

    public IdempotencyStatus getStatus() {
        return status;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }
}
