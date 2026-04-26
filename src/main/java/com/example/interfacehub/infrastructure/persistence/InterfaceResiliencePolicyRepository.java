package com.example.interfacehub.infrastructure.persistence;

import com.example.interfacehub.domain.interfaceconfig.InterfaceResiliencePolicy;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface InterfaceResiliencePolicyRepository extends JpaRepository<InterfaceResiliencePolicy, String> {

    Optional<InterfaceResiliencePolicy> findByInterfaceCode(String interfaceCode);
}

