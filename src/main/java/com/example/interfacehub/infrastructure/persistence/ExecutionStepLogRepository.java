package com.example.interfacehub.infrastructure.persistence;

import com.example.interfacehub.domain.execution.ExecutionStepLog;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ExecutionStepLogRepository extends JpaRepository<ExecutionStepLog, Long> {
    List<ExecutionStepLog> findByExecutionIdOrderByStepOrderAsc(String executionId);
}
