package com.example.interfacehub.domain.incident;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import java.time.LocalDateTime;

@Entity
public class IncidentSummary {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String summaryText;

    @Column(nullable = false)
    private Integer analyzedCount;

    @Column(nullable = false)
    private Integer hoursBack;

    @Column(nullable = false)
    private LocalDateTime generatedAt;

    protected IncidentSummary() {
    }

    public IncidentSummary(String summaryText, Integer analyzedCount, Integer hoursBack) {
        this.summaryText = summaryText;
        this.analyzedCount = analyzedCount;
        this.hoursBack = hoursBack;
        this.generatedAt = LocalDateTime.now();
    }

    public Long getId() {
        return id;
    }

    public String getSummaryText() {
        return summaryText;
    }

    public Integer getAnalyzedCount() {
        return analyzedCount;
    }

    public Integer getHoursBack() {
        return hoursBack;
    }

    public LocalDateTime getGeneratedAt() {
        return generatedAt;
    }
}
