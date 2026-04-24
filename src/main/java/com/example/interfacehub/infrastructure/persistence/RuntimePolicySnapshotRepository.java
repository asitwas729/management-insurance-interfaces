package com.example.interfacehub.infrastructure.persistence;

import com.example.interfacehub.domain.policy.RuntimePolicySnapshot;
import org.springframework.data.jpa.repository.JpaRepository;

public interface RuntimePolicySnapshotRepository extends JpaRepository<RuntimePolicySnapshot, Long> {
}
