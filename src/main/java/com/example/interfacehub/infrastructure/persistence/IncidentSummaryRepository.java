package com.example.interfacehub.infrastructure.persistence;

import com.example.interfacehub.domain.incident.IncidentSummary;
import org.springframework.data.jpa.repository.JpaRepository;

public interface IncidentSummaryRepository extends JpaRepository<IncidentSummary, Long> {
}
