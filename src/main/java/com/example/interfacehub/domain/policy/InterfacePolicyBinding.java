package com.example.interfacehub.domain.policy;

import com.example.interfacehub.domain.interfaceconfig.InterfaceDefinition;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.ManyToOne;
import java.time.LocalDateTime;
import org.hibernate.annotations.CreationTimestamp;

@Entity
public class InterfacePolicyBinding {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    private InterfaceDefinition interfaceDefinition;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    private PolicyTemplate policyTemplate;

    @Column(length = 100)
    private String partnerId;

    @Column(nullable = false)
    private Integer priority;

    @Column(nullable = false)
    private boolean enabled;

    @CreationTimestamp
    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    protected InterfacePolicyBinding() {
    }

    private InterfacePolicyBinding(
        InterfaceDefinition interfaceDefinition,
        PolicyTemplate policyTemplate,
        String partnerId,
        Integer priority
    ) {
        this.interfaceDefinition = interfaceDefinition;
        this.policyTemplate = policyTemplate;
        this.partnerId = partnerId;
        this.priority = priority;
        this.enabled = true;
    }

    public static InterfacePolicyBinding create(
        InterfaceDefinition interfaceDefinition,
        PolicyTemplate policyTemplate,
        String partnerId,
        Integer priority
    ) {
        return new InterfacePolicyBinding(interfaceDefinition, policyTemplate, partnerId, priority);
    }

    public Long getId() {
        return id;
    }

    public InterfaceDefinition getInterfaceDefinition() {
        return interfaceDefinition;
    }

    public PolicyTemplate getPolicyTemplate() {
        return policyTemplate;
    }

    public String getPartnerId() {
        return partnerId;
    }

    public Integer getPriority() {
        return priority;
    }

    public boolean isEnabled() {
        return enabled;
    }
}
