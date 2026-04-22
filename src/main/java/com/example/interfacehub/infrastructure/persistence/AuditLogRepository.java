package com.example.interfacehub.infrastructure.persistence;

import com.example.interfacehub.domain.audit.AuditLog;
import java.time.LocalDateTime;
import java.util.List;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface AuditLogRepository extends JpaRepository<AuditLog, Long> {

    List<AuditLog> findByTargetTypeAndTargetId(String targetType, String targetId);

    @Query("""
        select a from AuditLog a
        where (:actor is null or a.actor = :actor)
          and (:action is null or a.action = :action)
          and (:targetType is null or a.targetType = :targetType)
          and (:targetId is null or a.targetId = :targetId)
          and (:from is null or a.createdAt >= :from)
          and (:to is null or a.createdAt <= :to)
        """)
    Page<AuditLog> search(
        @Param("actor") String actor,
        @Param("action") String action,
        @Param("targetType") String targetType,
        @Param("targetId") String targetId,
        @Param("from") LocalDateTime from,
        @Param("to") LocalDateTime to,
        Pageable pageable
    );
}
