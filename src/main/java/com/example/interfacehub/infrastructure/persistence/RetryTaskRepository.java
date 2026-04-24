package com.example.interfacehub.infrastructure.persistence;

import com.example.interfacehub.domain.retry.RetryStatus;
import com.example.interfacehub.domain.retry.RetryTask;
import java.util.List;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface RetryTaskRepository extends JpaRepository<RetryTask, Long> {

    List<RetryTask> findByOriginalExecutionId(String originalExecutionId);

    List<RetryTask> findByStatus(RetryStatus status);

    Page<RetryTask> findByStatusOrderByCreatedAtDesc(RetryStatus status, Pageable pageable);

    Page<RetryTask> findAllByOrderByCreatedAtDesc(Pageable pageable);

    @Query("""
        select r
        from RetryTask r
        where lower(r.originalExecutionId) like lower(concat('%', :q, '%'))
           or lower(r.requester) like lower(concat('%', :q, '%'))
           or lower(r.requestReasonCode) like lower(concat('%', :q, '%'))
        order by r.createdAt desc
        """)
    Page<RetryTask> search(@Param("q") String q, Pageable pageable);
}
