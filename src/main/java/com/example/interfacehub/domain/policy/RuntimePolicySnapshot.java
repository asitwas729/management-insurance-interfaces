package com.example.interfacehub.domain.policy;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import java.time.LocalDateTime;
import org.hibernate.annotations.CreationTimestamp;

@Entity
public class RuntimePolicySnapshot {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 100)
    private String executionId;

    @Column(nullable = false, length = 100)
    private String interfaceCode;

    @Column(length = 100)
    private String partnerId;

    @Column(length = 100)
    private String clientId;

    @Column(nullable = false, length = 100)
    private String policyName;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String snapshotJson;

    @CreationTimestamp
    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    protected RuntimePolicySnapshot() {
    }

    private RuntimePolicySnapshot(
        String executionId,
        String interfaceCode,
        String partnerId,
        String clientId,
        String policyName,
        String snapshotJson
    ) {
        this.executionId = executionId;
        this.interfaceCode = interfaceCode;
        this.partnerId = partnerId;
        this.clientId = clientId;
        this.policyName = policyName;
        this.snapshotJson = snapshotJson;
    }

    public static RuntimePolicySnapshot create(
        String executionId,
        String interfaceCode,
        String partnerId,
        String clientId,
        String policyName,
        String snapshotJson
    ) {
        return new RuntimePolicySnapshot(executionId, interfaceCode, partnerId, clientId, policyName, snapshotJson);
    }
}
