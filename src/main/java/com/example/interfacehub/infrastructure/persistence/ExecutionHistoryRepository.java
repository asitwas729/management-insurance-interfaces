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

    long countByStatusAndStartedAtAfter(ExecutionStatus status, LocalDateTime since);

    long countByInterfaceCodeAndStatus(String interfaceCode, ExecutionStatus status);

    @Query("SELECT e FROM ExecutionHistory e WHERE e.status = :status AND e.startedAt >= :since ORDER BY e.startedAt DESC")
    List<ExecutionHistory> findRecentByStatus(
        @Param("status") ExecutionStatus status,
        @Param("since") LocalDateTime since,
        Pageable pageable
    );

    @Query("""
        select e
        from ExecutionHistory e
        where lower(e.executionId) like lower(concat('%', :q, '%'))
           or lower(e.interfaceCode) like lower(concat('%', :q, '%'))
           or lower(coalesce(e.errorCode, '')) like lower(concat('%', :q, '%'))
           or lower(coalesce(e.errorMessage, '')) like lower(concat('%', :q, '%'))
        order by e.startedAt desc
        """)
    Page<ExecutionHistory> search(@Param("q") String q, Pageable pageable);

    interface InterfaceStatProjection {
        String getInterfaceCode();

        Double getAvgLatency();

        LocalDateTime getLastExecutedAt();

        Long getTotal();

        Long getSuccessCount();
    }

    @Query("""
        SELECT e.interfaceCode AS interfaceCode,
               AVG(e.latencyMillis) AS avgLatency,
               MAX(e.startedAt) AS lastExecutedAt,
               COUNT(e) AS total,
               SUM(CASE WHEN e.status = :success THEN 1 ELSE 0 END) AS successCount
        FROM ExecutionHistory e
        WHERE e.startedAt >= :since
        GROUP BY e.interfaceCode
        """)
    List<InterfaceStatProjection> findInterfaceStatsSince(
        @Param("since") LocalDateTime since,
        @Param("success") ExecutionStatus success
    );
}
