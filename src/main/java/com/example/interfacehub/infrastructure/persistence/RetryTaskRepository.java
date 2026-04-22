package com.example.interfacehub.infrastructure.persistence;

import com.example.interfacehub.domain.retry.RetryStatus;
import com.example.interfacehub.domain.retry.RetryTask;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface RetryTaskRepository extends JpaRepository<RetryTask, Long> {

    List<RetryTask> findByOriginalExecutionId(String originalExecutionId);

    List<RetryTask> findByStatus(RetryStatus status);
}
