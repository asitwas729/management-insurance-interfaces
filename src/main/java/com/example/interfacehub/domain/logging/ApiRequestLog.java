package com.example.interfacehub.domain.logging;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import java.time.LocalDateTime;

@Entity
public class ApiRequestLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(length = 100)
    private String actor;

    @Column(nullable = false, length = 30)
    private String source;

    @Column(nullable = false, length = 10)
    private String method;

    @Column(nullable = false, length = 500)
    private String path;

    @Column(columnDefinition = "TEXT")
    private String requestBody;

    private Integer responseStatus;

    @Column(columnDefinition = "TEXT")
    private String responseBody;

    @Column(columnDefinition = "TEXT")
    private String errorMessage;

    private Long durationMs;

    @Column(nullable = false)
    private LocalDateTime occurredAt;

    @Column(nullable = false)
    private LocalDateTime createdAt;

    protected ApiRequestLog() {
    }

    private ApiRequestLog(
        String actor,
        String source,
        String method,
        String path,
        String requestBody,
        Integer responseStatus,
        String responseBody,
        String errorMessage,
        Long durationMs,
        LocalDateTime occurredAt
    ) {
        this.actor = actor;
        this.source = source;
        this.method = method;
        this.path = path;
        this.requestBody = requestBody;
        this.responseStatus = responseStatus;
        this.responseBody = responseBody;
        this.errorMessage = errorMessage;
        this.durationMs = durationMs;
        this.occurredAt = occurredAt == null ? LocalDateTime.now() : occurredAt;
        this.createdAt = LocalDateTime.now();
    }

    public static ApiRequestLog fromUi(
        String actor,
        String method,
        String path,
        String requestBody,
        Integer responseStatus,
        String responseBody,
        String errorMessage,
        Long durationMs,
        LocalDateTime occurredAt
    ) {
        return new ApiRequestLog(
            actor,
            "UI",
            method,
            path,
            requestBody,
            responseStatus,
            responseBody,
            errorMessage,
            durationMs,
            occurredAt
        );
    }

    public Long getId() {
        return id;
    }

    public String getActor() {
        return actor;
    }

    public String getSource() {
        return source;
    }

    public String getMethod() {
        return method;
    }

    public String getPath() {
        return path;
    }

    public String getRequestBody() {
        return requestBody;
    }

    public Integer getResponseStatus() {
        return responseStatus;
    }

    public String getResponseBody() {
        return responseBody;
    }

    public String getErrorMessage() {
        return errorMessage;
    }

    public Long getDurationMs() {
        return durationMs;
    }

    public LocalDateTime getOccurredAt() {
        return occurredAt;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }
}

