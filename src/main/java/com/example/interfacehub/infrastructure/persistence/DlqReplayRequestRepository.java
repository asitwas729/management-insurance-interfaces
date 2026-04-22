package com.example.interfacehub.infrastructure.persistence;

import com.example.interfacehub.domain.mq.DlqReplayRequest;
import com.example.interfacehub.domain.mq.DlqReplayStatus;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

public interface DlqReplayRequestRepository extends JpaRepository<DlqReplayRequest, Long> {

    @EntityGraph(attributePaths = {"dlqMessage"})
    Optional<DlqReplayRequest> findById(Long id);

    @EntityGraph(attributePaths = {"dlqMessage"})
    List<DlqReplayRequest> findByStatusOrderByCreatedAtDesc(DlqReplayStatus status);

    @EntityGraph(attributePaths = {"dlqMessage"})
    List<DlqReplayRequest> findAllByOrderByCreatedAtDesc();

    @EntityGraph(attributePaths = {"dlqMessage"})
    @Query("""
        select request from DlqReplayRequest request
        where (:status is null or request.status = :status)
          and (:fromAt is null or request.createdAt >= :fromAt)
          and (:toAt is null or request.createdAt < :toAt)
        """)
    Page<DlqReplayRequest> search(
        @Param("status") DlqReplayStatus status,
        @Param("fromAt") LocalDateTime fromAt,
        @Param("toAt") LocalDateTime toAt,
        Pageable pageable
    );
}
