package com.example.interfacehub.infrastructure.persistence;

import com.example.interfacehub.domain.interfaceconfig.InterfaceDefinition;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface InterfaceDefinitionRepository extends JpaRepository<InterfaceDefinition, Long> {

    Optional<InterfaceDefinition> findByInterfaceCode(String interfaceCode);

    boolean existsByInterfaceCode(String interfaceCode);
}
