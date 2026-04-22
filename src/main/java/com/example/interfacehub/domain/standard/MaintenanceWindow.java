package com.example.interfacehub.domain.standard;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import java.time.DayOfWeek;
import java.time.LocalDateTime;
import java.time.LocalTime;

@Entity
public class MaintenanceWindow {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 100)
    private String externalOrg;

    @Column(nullable = false, length = 12)
    private String dayOfWeek;

    @Column(nullable = false)
    private LocalTime startTime;

    @Column(nullable = false)
    private LocalTime endTime;

    @Column(nullable = false, length = 20)
    private String suppressLevel;

    @Column(nullable = false)
    private boolean enabled;

    @Column(length = 500)
    private String reason;

    @Column(nullable = false)
    private LocalDateTime updatedAt;

    protected MaintenanceWindow() {
    }

    private MaintenanceWindow(
        String externalOrg,
        DayOfWeek dayOfWeek,
        LocalTime startTime,
        LocalTime endTime,
        String suppressLevel,
        String reason
    ) {
        this.externalOrg = externalOrg;
        this.dayOfWeek = dayOfWeek.name();
        this.startTime = startTime;
        this.endTime = endTime;
        this.suppressLevel = suppressLevel;
        this.enabled = true;
        this.reason = reason;
        this.updatedAt = LocalDateTime.now();
    }

    public static MaintenanceWindow create(
        String externalOrg,
        DayOfWeek dayOfWeek,
        LocalTime startTime,
        LocalTime endTime,
        String suppressLevel,
        String reason
    ) {
        return new MaintenanceWindow(externalOrg, dayOfWeek, startTime, endTime, suppressLevel, reason);
    }

    public void update(
        DayOfWeek dayOfWeek,
        LocalTime startTime,
        LocalTime endTime,
        String suppressLevel,
        String reason,
        boolean enabled
    ) {
        this.dayOfWeek = dayOfWeek.name();
        this.startTime = startTime;
        this.endTime = endTime;
        this.suppressLevel = suppressLevel;
        this.reason = reason;
        this.enabled = enabled;
        this.updatedAt = LocalDateTime.now();
    }

    public Long getId() {
        return id;
    }

    public String getExternalOrg() {
        return externalOrg;
    }

    public String getDayOfWeek() {
        return dayOfWeek;
    }

    public LocalTime getStartTime() {
        return startTime;
    }

    public LocalTime getEndTime() {
        return endTime;
    }

    public String getSuppressLevel() {
        return suppressLevel;
    }

    public boolean isEnabled() {
        return enabled;
    }

    public String getReason() {
        return reason;
    }
}
