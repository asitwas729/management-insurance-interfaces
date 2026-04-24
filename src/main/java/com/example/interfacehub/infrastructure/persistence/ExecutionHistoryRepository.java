package com.example.interfacehub.infrastructure.persistence;

import com.example.interfacehub.domain.execution.ExecutionHistory;
import com.example.interfacehub.domain.execution.ExecutionStatus;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ExecutionHistoryRepository extends JpaRepository<ExecutionHistory, Long>, JpaSpecificationExecutor<ExecutionHistory> {

    Page<ExecutionHistory> findByInterfaceCode(String interfaceCode, Pageable pageable);

    Optional<ExecutionHistory> findByExecutionId(String executionId);

    Optional<ExecutionHistory> findFirstByInterfaceCodeOrderByStartedAtDesc(String interfaceCode);

    long countByStatus(ExecutionStatus status);

    long countByInterfaceCodeAndStatus(String interfaceCode, ExecutionStatus status);

    @Query("SELECT e FROM ExecutionHistory e WHERE e.status = :status AND e.startedAt >= :since ORDER BY e.startedAt DESC")
    List<ExecutionHistory> findRecentByStatus(
        @Param("status") ExecutionStatus status,
        @Param("since") LocalDateTime since,
        Pageable pageable
    );
}
