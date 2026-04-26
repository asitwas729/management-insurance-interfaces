package com.example.interfacehub.domain.mq;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Lob;
import jakarta.persistence.Table;
import java.time.Duration;
import java.time.LocalDateTime;
import org.hibernate.annotations.CreationTimestamp;

@Entity
@Table(name = "dlq_message")
public class DlqMessage {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 100)
    private String interfaceCode;

    @Column(nullable = false, length = 500)
    private String topic;

    @Lob
    @Column(nullable = false, columnDefinition = "TEXT")
    private String payload;

    @Column(nullable = false, length = 1000)
    private String reason;

    @Column(nullable = false)
    private int replayCount;

    private LocalDateTime lastReplayedAt;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private DlqMessageStatus status;

    @CreationTimestamp
    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    protected DlqMessage() {
    }

    private DlqMessage(String interfaceCode, String topic, String payload, String reason) {
        this.interfaceCode = interfaceCode;
        this.topic = topic;
        this.payload = payload;
        this.reason = reason;
        this.replayCount = 0;
        this.status = DlqMessageStatus.PENDING;
    }

    public static DlqMessage create(String interfaceCode, String topic, String payload, String reason) {
        return new DlqMessage(interfaceCode, topic, payload, reason);
    }

    public void markReplayed() {
        if (this.status == DlqMessageStatus.EXHAUSTED) {
            return;
        }
        this.replayCount += 1;
        this.lastReplayedAt = LocalDateTime.now();
        this.status = DlqMessageStatus.REPLAYED;
    }

    public void markExhausted() {
        this.status = DlqMessageStatus.EXHAUSTED;
    }

    public boolean exceedsReplayLimit(int maxAttempts) {
        return this.replayCount >= maxAttempts;
    }

    public boolean isInCooldown(long cooldownSeconds) {
        if (cooldownSeconds <= 0) {
            return false;
        }
        if (lastReplayedAt == null) {
            return false;
        }
        long elapsedSeconds = Duration.between(lastReplayedAt, LocalDateTime.now()).getSeconds();
        if (elapsedSeconds < 0) {
            return false;
        }
        return elapsedSeconds < cooldownSeconds;
    }

    public Long getId() {
        return id;
    }

    public String getInterfaceCode() {
        return interfaceCode;
    }

    public String getTopic() {
        return topic;
    }

    public String getPayload() {
        return payload;
    }

    public String getReason() {
        return reason;
    }

    public int getReplayCount() {
        return replayCount;
    }

    public LocalDateTime getLastReplayedAt() {
        return lastReplayedAt;
    }

    public DlqMessageStatus getStatus() {
        return status;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }
}
