package com.example.interfacehub.infrastructure.persistence;

import com.example.interfacehub.domain.execution.IdempotencyRecord;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface IdempotencyRecordRepository extends JpaRepository<IdempotencyRecord, Long> {

    boolean existsByIdempotencyKey(String idempotencyKey);

    Optional<IdempotencyRecord> findByIdempotencyKey(String idempotencyKey);

    Optional<IdempotencyRecord> findByExecutionId(String executionId);
}
