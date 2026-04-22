package com.example.interfacehub.domain.retry;

import com.example.interfacehub.domain.interfaceconfig.InterfaceDefinition;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.ManyToOne;
import java.time.LocalDateTime;

@Entity
public class RetryTask {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.EAGER, optional = false)
    private InterfaceDefinition interfaceDefinition;

    @Column(nullable = false, length = 100)
    private String originalExecutionId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private RetryStatus status;

    @Column(nullable = false, length = 100)
    private String requester;

    @Column(nullable = false, length = 100)
    private String requestReasonCode;

    @Column(length = 1000)
    private String requestReasonDetail;

    @Column(length = 100)
    private String approver;

    @Column(length = 1000)
    private String rejectReason;

    @Column(nullable = false)
    private LocalDateTime createdAt;

    private LocalDateTime approvedAt;

    private LocalDateTime executedAt;

    protected RetryTask() {
    }

    private RetryTask(
        InterfaceDefinition interfaceDefinition,
        String originalExecutionId,
        String requester,
        String requestReasonCode,
        String requestReasonDetail
    ) {
        this.interfaceDefinition = interfaceDefinition;
        this.originalExecutionId = originalExecutionId;
        this.requester = requester;
        this.requestReasonCode = requestReasonCode;
        this.requestReasonDetail = requestReasonDetail;
        this.status = RetryStatus.PENDING;
        this.createdAt = LocalDateTime.now();
    }

    public static RetryTask request(
        InterfaceDefinition interfaceDefinition,
        String originalExecutionId,
        String requester,
        String requestReasonCode,
        String requestReasonDetail
    ) {
        return new RetryTask(interfaceDefinition, originalExecutionId, requester, requestReasonCode, requestReasonDetail);
    }

    public void approve(String approver) {
        this.approver = approver;
        this.status = RetryStatus.APPROVED;
        this.approvedAt = LocalDateTime.now();
    }

    public void reject(String approver, String rejectReason) {
        this.approver = approver;
        this.rejectReason = rejectReason;
        this.status = RetryStatus.REJECTED;
        this.approvedAt = LocalDateTime.now();
    }

    public void markExecuted() {
        this.status = RetryStatus.EXECUTED;
        this.executedAt = LocalDateTime.now();
    }

    public void markFailed() {
        this.status = RetryStatus.FAILED;
        this.executedAt = LocalDateTime.now();
    }

    public Long getId() {
        return id;
    }

    public InterfaceDefinition getInterfaceDefinition() {
        return interfaceDefinition;
    }

    public String getOriginalExecutionId() {
        return originalExecutionId;
    }

    public RetryStatus getStatus() {
        return status;
    }

    public String getRequester() {
        return requester;
    }

    public String getRequestReasonCode() {
        return requestReasonCode;
    }

    public String getRequestReasonDetail() {
        return requestReasonDetail;
    }

    public String getApprover() {
        return approver;
    }

    public String getRejectReason() {
        return rejectReason;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public LocalDateTime getApprovedAt() {
        return approvedAt;
    }

    public LocalDateTime getExecutedAt() {
        return executedAt;
    }
}
