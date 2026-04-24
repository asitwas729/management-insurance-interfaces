package com.example.interfacehub.infrastructure.persistence;

import com.example.interfacehub.domain.interfaceconfig.InterfaceDefinition;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface InterfaceDefinitionRepository extends JpaRepository<InterfaceDefinition, Long> {

    Optional<InterfaceDefinition> findByInterfaceCode(String interfaceCode);

    boolean existsByInterfaceCode(String interfaceCode);

    @Query("""
        select d
        from InterfaceDefinition d
        where lower(d.interfaceCode) like lower(concat('%', :q, '%'))
           or lower(d.name) like lower(concat('%', :q, '%'))
           or lower(d.ownerTeam) like lower(concat('%', :q, '%'))
           or lower(d.businessCategory) like lower(concat('%', :q, '%'))
           or lower(d.externalOrg) like lower(concat('%', :q, '%'))
        order by d.interfaceCode asc
        """)
    Page<InterfaceDefinition> search(@Param("q") String q, Pageable pageable);
}
