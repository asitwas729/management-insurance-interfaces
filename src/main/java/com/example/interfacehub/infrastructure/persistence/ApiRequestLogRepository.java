package com.example.interfacehub.infrastructure.persistence;

import com.example.interfacehub.domain.logging.ApiRequestLog;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ApiRequestLogRepository extends JpaRepository<ApiRequestLog, Long> {

    Page<ApiRequestLog> findAllByOrderByCreatedAtDesc(Pageable pageable);

    @Query("""
        select l
        from ApiRequestLog l
        where lower(l.path) like lower(concat('%', :q, '%'))
           or lower(l.method) like lower(concat('%', :q, '%'))
           or lower(coalesce(l.errorMessage, '')) like lower(concat('%', :q, '%'))
        order by l.createdAt desc
        """)
    Page<ApiRequestLog> search(@Param("q") String q, Pageable pageable);
}

