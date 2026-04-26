package com.example.interfacehub.domain.interfaceconfig;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import org.hibernate.annotations.UpdateTimestamp;

@Entity
@Table(name = "interface_resilience_policy")
public class InterfaceResiliencePolicy {

    @Id
    @Column(length = 100)
    private String interfaceCode;

    @Column(nullable = false)
    private float cbFailureRateThreshold;

    @Column(nullable = false)
    private int cbSlidingWindow;

    @Column(nullable = false)
    private int rateLimitPerSecond;

    @Column(nullable = false)
    private long timeoutMillis;

    @Column(nullable = false)
    private int retryMaxAttempts;

    @UpdateTimestamp
    @Column(nullable = false)
    private LocalDateTime updatedAt;

    protected InterfaceResiliencePolicy() {
    }

    private InterfaceResiliencePolicy(
        String interfaceCode,
        float cbFailureRateThreshold,
        int cbSlidingWindow,
        int rateLimitPerSecond,
        long timeoutMillis,
        int retryMaxAttempts
    ) {
        this.interfaceCode = interfaceCode;
        this.cbFailureRateThreshold = cbFailureRateThreshold;
        this.cbSlidingWindow = cbSlidingWindow;
        this.rateLimitPerSecond = rateLimitPerSecond;
        this.timeoutMillis = timeoutMillis;
        this.retryMaxAttempts = retryMaxAttempts;
    }

    public static InterfaceResiliencePolicy create(
        String interfaceCode,
        float cbFailureRateThreshold,
        int cbSlidingWindow,
        int rateLimitPerSecond,
        long timeoutMillis,
        int retryMaxAttempts
    ) {
        return new InterfaceResiliencePolicy(
            interfaceCode,
            cbFailureRateThreshold,
            cbSlidingWindow,
            rateLimitPerSecond,
            timeoutMillis,
            retryMaxAttempts
        );
    }

    public void update(
        float cbFailureRateThreshold,
        int cbSlidingWindow,
        int rateLimitPerSecond,
        long timeoutMillis,
        int retryMaxAttempts
    ) {
        this.cbFailureRateThreshold = cbFailureRateThreshold;
        this.cbSlidingWindow = cbSlidingWindow;
        this.rateLimitPerSecond = rateLimitPerSecond;
        this.timeoutMillis = timeoutMillis;
        this.retryMaxAttempts = retryMaxAttempts;
    }

    public String getInterfaceCode() {
        return interfaceCode;
    }

    public float getCbFailureRateThreshold() {
        return cbFailureRateThreshold;
    }

    public int getCbSlidingWindow() {
        return cbSlidingWindow;
    }

    public int getRateLimitPerSecond() {
        return rateLimitPerSecond;
    }

    public long getTimeoutMillis() {
        return timeoutMillis;
    }

    public int getRetryMaxAttempts() {
        return retryMaxAttempts;
    }

    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }
}

