package com.example.interfacehub.domain.scheduler;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;
import java.time.LocalDateTime;

@Entity
@Table(name = "interface_schedule")
public class InterfaceSchedule {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "interface_code", nullable = false, unique = true, length = 100)
    private String interfaceCode;

    @Column(name = "cron_expression", nullable = false, length = 100)
    private String cronExpression;

    @Column(name = "enabled", nullable = false)
    private boolean enabled = true;

    @Column(name = "payload_template", columnDefinition = "TEXT")
    private String payloadTemplate;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    protected InterfaceSchedule() {}

    public static InterfaceSchedule create(String interfaceCode, String cronExpression, String payloadTemplate) {
        InterfaceSchedule schedule = new InterfaceSchedule();
        schedule.interfaceCode = interfaceCode;
        schedule.cronExpression = cronExpression;
        schedule.payloadTemplate = payloadTemplate;
        schedule.enabled = true;
        return schedule;
    }

    public void updateCron(String cronExpression) {
        this.cronExpression = cronExpression;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public Long getId() { return id; }
    public String getInterfaceCode() { return interfaceCode; }
    public String getCronExpression() { return cronExpression; }
    public boolean isEnabled() { return enabled; }
    public String getPayloadTemplate() { return payloadTemplate; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }
}
