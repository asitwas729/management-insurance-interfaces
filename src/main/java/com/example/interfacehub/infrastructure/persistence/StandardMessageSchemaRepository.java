package com.example.interfacehub.infrastructure.persistence;

import com.example.interfacehub.domain.standardmessage.StandardMessageSchema;
import com.example.interfacehub.domain.standardmessage.StandardMessageSchemaId;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface StandardMessageSchemaRepository extends JpaRepository<StandardMessageSchema, StandardMessageSchemaId> {

    Optional<StandardMessageSchema> findByIdSchemaCodeAndIdVersionAndEnabledTrue(String schemaCode, Integer version);
}

