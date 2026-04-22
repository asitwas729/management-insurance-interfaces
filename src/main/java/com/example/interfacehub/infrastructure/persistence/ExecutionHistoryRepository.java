package com.example.interfacehub.infrastructure.persistence;

import com.example.interfacehub.domain.execution.ExecutionHistory;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ExecutionHistoryRepository extends JpaRepository<ExecutionHistory, Long> {

    Page<ExecutionHistory> findByInterfaceCode(String interfaceCode, Pageable pageable);

    Optional<ExecutionHistory> findByExecutionId(String executionId);
}
