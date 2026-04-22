package com.example.interfacehub.domain.mq;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Lob;
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
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
    }

    public static DlqMessage create(String interfaceCode, String topic, String payload, String reason) {
        return new DlqMessage(interfaceCode, topic, payload, reason);
    }

    public void markReplayed() {
        this.replayCount += 1;
        this.lastReplayedAt = LocalDateTime.now();
    }

    public boolean exceedsReplayLimit(int maxAttempts) {
        return this.replayCount >= maxAttempts;
    }

    public boolean isInCooldown(long cooldownSeconds) {
        if (lastReplayedAt == null) {
            return false;
        }
        long elapsed = ChronoUnit.SECONDS.between(lastReplayedAt, LocalDateTime.now());
        return elapsed < cooldownSeconds;
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

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }
}
