package com.example.interfacehub.infrastructure.persistence;

import com.example.interfacehub.domain.interfaceconfig.InterfaceConfigVersion;
import com.example.interfacehub.domain.interfaceconfig.InterfaceDefinition;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface InterfaceConfigVersionRepository extends JpaRepository<InterfaceConfigVersion, Long> {

    Optional<InterfaceConfigVersion> findByInterfaceDefinitionAndPublishedTrue(InterfaceDefinition interfaceDefinition);

    List<InterfaceConfigVersion> findByInterfaceDefinition(InterfaceDefinition interfaceDefinition);

    @Query("select coalesce(max(c.version), 0) from InterfaceConfigVersion c where c.interfaceDefinition = :definition")
    int findMaxVersionByInterfaceDefinition(@Param("definition") InterfaceDefinition definition);
}
