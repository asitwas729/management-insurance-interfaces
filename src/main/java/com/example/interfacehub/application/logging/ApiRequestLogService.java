package com.example.interfacehub.application.logging;

import com.example.interfacehub.domain.logging.ApiRequestLog;
import com.example.interfacehub.infrastructure.persistence.ApiRequestLogRepository;
import com.example.interfacehub.presentation.CreateApiRequestLogRequest;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ApiRequestLogService {

    private final ApiRequestLogRepository apiRequestLogRepository;
    private final CentralLoggingService centralLoggingService;

    public ApiRequestLogService(ApiRequestLogRepository apiRequestLogRepository, CentralLoggingService centralLoggingService) {
        this.apiRequestLogRepository = apiRequestLogRepository;
        this.centralLoggingService = centralLoggingService;
    }

    @Transactional
    public ApiRequestLog saveUiLog(String actor, CreateApiRequestLogRequest request) {
        Instant occurred = request.occurredAt();
        LocalDateTime occurredAt = occurred == null
            ? null
            : LocalDateTime.ofInstant(occurred, ZoneId.systemDefault());
        ApiRequestLog entry = ApiRequestLog.fromUi(
            actor,
            request.method(),
            request.path(),
            request.requestBody(),
            request.responseStatus(),
            request.responseBody(),
            request.errorMessage(),
            request.durationMs(),
            occurredAt
        );
        ApiRequestLog saved = apiRequestLogRepository.save(entry);
        centralLoggingService.sendApiRequestLog(saved);
        return saved;
    }

    @Transactional(readOnly = true)
    public Page<ApiRequestLog> findAll(Pageable pageable) {
        return apiRequestLogRepository.findAllByOrderByCreatedAtDesc(pageable);
    }

    @Transactional(readOnly = true)
    public Page<ApiRequestLog> search(String q, Pageable pageable) {
        if (q == null || q.isBlank()) {
            return findAll(pageable);
        }
        return apiRequestLogRepository.search(q.trim(), pageable);
    }
}
