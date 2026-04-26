package com.example.interfacehub.domain.standard;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import java.time.LocalDateTime;
import org.hibernate.annotations.CreationTimestamp;

@Entity
public class ErrorCatalog {

    @Id
    @Column(nullable = false, length = 50)
    private String code;

    @Column(nullable = false, length = 50)
    private String domain;

    @Column(nullable = false, length = 20)
    private String severity;

    @Column(nullable = false)
    private int httpStatus;

    @Column(nullable = false)
    private boolean retriable;

    @Column(nullable = false, length = 100)
    private String nextAction;

    @Column(nullable = false, length = 500)
    private String description;

    @CreationTimestamp
    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    protected ErrorCatalog() {
    }

    private ErrorCatalog(
        String code,
        String domain,
        String severity,
        int httpStatus,
        boolean retriable,
        String nextAction,
        String description
    ) {
        this.code = code;
        this.domain = domain;
        this.severity = severity;
        this.httpStatus = httpStatus;
        this.retriable = retriable;
        this.nextAction = nextAction;
        this.description = description;
    }

    public static ErrorCatalog create(
        String code,
        String domain,
        String severity,
        int httpStatus,
        boolean retriable,
        String nextAction,
        String description
    ) {
        return new ErrorCatalog(code, domain, severity, httpStatus, retriable, nextAction, description);
    }

    public void update(
        String domain,
        String severity,
        int httpStatus,
        boolean retriable,
        String nextAction,
        String description
    ) {
        this.domain = domain;
        this.severity = severity;
        this.httpStatus = httpStatus;
        this.retriable = retriable;
        this.nextAction = nextAction;
        this.description = description;
    }

    public String getCode() {
        return code;
    }

    public String getDomain() {
        return domain;
    }

    public String getSeverity() {
        return severity;
    }

    public int getHttpStatus() {
        return httpStatus;
    }

    public boolean isRetriable() {
        return retriable;
    }

    public String getNextAction() {
        return nextAction;
    }

    public String getDescription() {
        return description;
    }
}
