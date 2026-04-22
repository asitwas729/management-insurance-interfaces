package com.example.interfacehub.domain.mq;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import org.hibernate.annotations.CreationTimestamp;

@Entity
@Table(name = "dlq_replay_request")
public class DlqReplayRequest {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "dlq_message_id", nullable = false)
    private DlqMessage dlqMessage;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private DlqReplayStatus status;

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

    @Column(columnDefinition = "TEXT")
    private String payloadOverrideJson;

    private LocalDateTime approvedAt;

    private LocalDateTime executedAt;

    @CreationTimestamp
    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    protected DlqReplayRequest() {
    }

    private DlqReplayRequest(
        DlqMessage dlqMessage,
        String requester,
        String requestReasonCode,
        String requestReasonDetail,
        String payloadOverrideJson
    ) {
        this.dlqMessage = dlqMessage;
        this.requester = requester;
        this.requestReasonCode = requestReasonCode;
        this.requestReasonDetail = requestReasonDetail;
        this.payloadOverrideJson = payloadOverrideJson;
        this.status = DlqReplayStatus.PENDING;
    }

    public static DlqReplayRequest request(
        DlqMessage dlqMessage,
        String requester,
        String requestReasonCode,
        String requestReasonDetail,
        String payloadOverrideJson
    ) {
        return new DlqReplayRequest(dlqMessage, requester, requestReasonCode, requestReasonDetail, payloadOverrideJson);
    }

    public void approve(String approver) {
        this.approver = approver;
        this.status = DlqReplayStatus.APPROVED;
        this.approvedAt = LocalDateTime.now();
    }

    public void reject(String approver, String reason) {
        this.approver = approver;
        this.rejectReason = reason;
        this.status = DlqReplayStatus.REJECTED;
    }

    public void markExecuted() {
        this.status = DlqReplayStatus.EXECUTED;
        this.executedAt = LocalDateTime.now();
    }

    public void markFailed() {
        this.status = DlqReplayStatus.FAILED;
        this.executedAt = LocalDateTime.now();
    }

    public Long getId() {
        return id;
    }

    public DlqMessage getDlqMessage() {
        return dlqMessage;
    }

    public DlqReplayStatus getStatus() {
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

    public String getPayloadOverrideJson() {
        return payloadOverrideJson;
    }

    public LocalDateTime getApprovedAt() {
        return approvedAt;
    }

    public LocalDateTime getExecutedAt() {
        return executedAt;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }
}
