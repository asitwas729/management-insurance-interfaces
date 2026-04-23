package com.example.interfacehub.domain.interfaceconfig;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.ManyToOne;
import java.time.LocalDateTime;

@Entity
public class InterfaceConfigVersion {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    private InterfaceDefinition interfaceDefinition;

    @Column(nullable = false)
    private Integer version;

    @Column(nullable = false, length = 1000)
    private String endpoint;

    @Column(length = 50)
    private String authType;

    @Column(columnDefinition = "TEXT")
    private String headersJson;

    @Column(nullable = false)
    private Long timeoutMillis;

    @Column(nullable = false)
    private boolean published;

    @Column(nullable = false)
    private boolean sandboxMode = false;

    @Column
    private Integer mockHttpStatus;

    @Column(columnDefinition = "TEXT")
    private String mockResponseBody;

    @Column(nullable = false)
    private LocalDateTime createdAt;

    protected InterfaceConfigVersion() {
    }

    private InterfaceConfigVersion(
        InterfaceDefinition interfaceDefinition,
        Integer version,
        String endpoint,
        String authType,
        String headersJson,
        Long timeoutMillis
    ) {
        this.interfaceDefinition = interfaceDefinition;
        this.version = version;
        this.endpoint = endpoint;
        this.authType = authType;
        this.headersJson = headersJson;
        this.timeoutMillis = timeoutMillis;
        this.published = false;
        this.sandboxMode = false;
        this.createdAt = LocalDateTime.now();
    }

    public static InterfaceConfigVersion create(
        InterfaceDefinition interfaceDefinition,
        Integer version,
        String endpoint,
        String authType,
        String headersJson,
        Long timeoutMillis
    ) {
        return new InterfaceConfigVersion(interfaceDefinition, version, endpoint, authType, headersJson, timeoutMillis);
    }

    public void configureSandbox(boolean sandboxMode, Integer mockHttpStatus, String mockResponseBody) {
        this.sandboxMode = sandboxMode;
        this.mockHttpStatus = mockHttpStatus;
        this.mockResponseBody = mockResponseBody;
    }

    public void publish() {
        this.published = true;
    }

    public void unpublish() {
        this.published = false;
    }

    public Long getId() {
        return id;
    }

    public InterfaceDefinition getInterfaceDefinition() {
        return interfaceDefinition;
    }

    public Integer getVersion() {
        return version;
    }

    public String getEndpoint() {
        return endpoint;
    }

    public String getAuthType() {
        return authType;
    }

    public String getHeadersJson() {
        return headersJson;
    }

    public Long getTimeoutMillis() {
        return timeoutMillis;
    }

    public boolean isPublished() {
        return published;
    }

    public boolean isSandboxMode() {
        return sandboxMode;
    }

    public Integer getMockHttpStatus() {
        return mockHttpStatus;
    }

    public String getMockResponseBody() {
        return mockResponseBody;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }
}
