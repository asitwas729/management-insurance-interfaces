package com.example.interfacehub.infrastructure.persistence;

import com.example.interfacehub.domain.interfaceconfig.InterfaceConfigVersion;
import com.example.interfacehub.domain.interfaceconfig.InterfaceDefinition;
import com.example.interfacehub.domain.interfaceconfig.RuntimeEnvironment;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface InterfaceConfigVersionRepository extends JpaRepository<InterfaceConfigVersion, Long> {

    @EntityGraph(attributePaths = {"interfaceDefinition"})
    Optional<InterfaceConfigVersion> findByInterfaceDefinitionAndPublishedTrue(InterfaceDefinition interfaceDefinition);

    @EntityGraph(attributePaths = {"interfaceDefinition"})
    Optional<InterfaceConfigVersion> findByInterfaceDefinitionAndEnvironmentAndPublishedTrue(
        InterfaceDefinition interfaceDefinition,
        RuntimeEnvironment environment
    );

    @EntityGraph(attributePaths = {"interfaceDefinition"})
    List<InterfaceConfigVersion> findByInterfaceDefinition(InterfaceDefinition interfaceDefinition);

    @EntityGraph(attributePaths = {"interfaceDefinition"})
    List<InterfaceConfigVersion> findByInterfaceDefinitionAndEnvironment(
        InterfaceDefinition interfaceDefinition,
        RuntimeEnvironment environment
    );

    @EntityGraph(attributePaths = {"interfaceDefinition"})
    Page<InterfaceConfigVersion> findByInterfaceDefinitionOrderByVersionDesc(InterfaceDefinition interfaceDefinition, Pageable pageable);

    @EntityGraph(attributePaths = {"interfaceDefinition"})
    Page<InterfaceConfigVersion> findByInterfaceDefinitionAndEnvironmentOrderByVersionDesc(
        InterfaceDefinition interfaceDefinition,
        RuntimeEnvironment environment,
        Pageable pageable
    );

    int countByInterfaceDefinition(InterfaceDefinition interfaceDefinition);

    @Query("select coalesce(max(c.version), 0) from InterfaceConfigVersion c where c.interfaceDefinition = :definition")
    int findMaxVersionByInterfaceDefinition(@Param("definition") InterfaceDefinition definition);

    @EntityGraph(attributePaths = {"interfaceDefinition"})
    @Query("""
        select c
        from InterfaceConfigVersion c
        join c.interfaceDefinition d
        where lower(d.interfaceCode) like lower(concat('%', :q, '%'))
           or lower(d.name) like lower(concat('%', :q, '%'))
           or lower(c.endpoint) like lower(concat('%', :q, '%'))
           or lower(coalesce(c.authType, '')) like lower(concat('%', :q, '%'))
        order by c.createdAt desc
        """)
    Page<InterfaceConfigVersion> search(@Param("q") String q, Pageable pageable);
}
