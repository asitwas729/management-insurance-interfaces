package com.example.interfacehub.infrastructure.persistence;

import com.example.interfacehub.domain.policy.PolicyTemplate;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PolicyTemplateRepository extends JpaRepository<PolicyTemplate, Long> {
    Optional<PolicyTemplate> findByPolicyName(String policyName);
}
