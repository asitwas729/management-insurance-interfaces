package com.example.interfacehub.domain.interfaceconfig;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import java.time.LocalDateTime;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

@Entity
public class InterfaceDefinition {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 100)
    private String interfaceCode;

    @Column(nullable = false, length = 200)
    private String name;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private ProtocolType protocolType;

    @Column(nullable = false, length = 100)
    private String ownerTeam;

    @Column(nullable = false, length = 100)
    private String externalOrg;

    private Long slaMillis;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private InterfaceStatus status;

    @CreationTimestamp
    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(nullable = false)
    private LocalDateTime updatedAt;

    protected InterfaceDefinition() {
    }

    private InterfaceDefinition(
        String interfaceCode,
        String name,
        ProtocolType protocolType,
        String ownerTeam,
        String externalOrg,
        Long slaMillis
    ) {
        this.interfaceCode = interfaceCode;
        this.name = name;
        this.protocolType = protocolType;
        this.ownerTeam = ownerTeam;
        this.externalOrg = externalOrg;
        this.slaMillis = slaMillis;
        this.status = InterfaceStatus.ACTIVE;
    }

    public static InterfaceDefinition create(
        String interfaceCode,
        String name,
        ProtocolType protocolType,
        String ownerTeam,
        String externalOrg,
        Long slaMillis
    ) {
        return new InterfaceDefinition(interfaceCode, name, protocolType, ownerTeam, externalOrg, slaMillis);
    }

    public Long getId() {
        return id;
    }

    public String getInterfaceCode() {
        return interfaceCode;
    }

    public String getName() {
        return name;
    }

    public ProtocolType getProtocolType() {
        return protocolType;
    }

    public String getOwnerTeam() {
        return ownerTeam;
    }

    public String getExternalOrg() {
        return externalOrg;
    }

    public Long getSlaMillis() {
        return slaMillis;
    }

    public InterfaceStatus getStatus() {
        return status;
    }
}
