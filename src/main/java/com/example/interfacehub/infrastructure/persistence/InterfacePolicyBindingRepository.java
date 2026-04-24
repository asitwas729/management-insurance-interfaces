package com.example.interfacehub.infrastructure.persistence;

import com.example.interfacehub.domain.interfaceconfig.InterfaceDefinition;
import com.example.interfacehub.domain.policy.InterfacePolicyBinding;
import java.util.List;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

public interface InterfacePolicyBindingRepository extends JpaRepository<InterfacePolicyBinding, Long> {

    @EntityGraph(attributePaths = {"interfaceDefinition", "policyTemplate"})
    List<InterfacePolicyBinding> findByInterfaceDefinitionAndEnabledTrueOrderByPriorityDesc(InterfaceDefinition definition);
}
