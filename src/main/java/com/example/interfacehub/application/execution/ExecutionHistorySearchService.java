package com.example.interfacehub.application.execution;

import com.example.interfacehub.domain.execution.ExecutionHistory;
import com.example.interfacehub.domain.execution.ExecutionStatus;
import com.example.interfacehub.domain.execution.TriggerType;
import com.example.interfacehub.domain.interfaceconfig.ProtocolType;
import com.example.interfacehub.infrastructure.persistence.ExecutionHistoryRepository;
import java.time.LocalDateTime;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

@Service
public class ExecutionHistorySearchService {

    private final ExecutionHistoryRepository executionHistoryRepository;

    public ExecutionHistorySearchService(ExecutionHistoryRepository executionHistoryRepository) {
        this.executionHistoryRepository = executionHistoryRepository;
    }

    @Transactional(readOnly = true)
    public Page<ExecutionHistory> search(ExecutionHistorySearchCriteria criteria, Pageable pageable) {
        Specification<ExecutionHistory> spec = Specification.where(null);

        if (criteria.protocolType() != null) {
            spec = spec.and((root, query, cb) -> cb.equal(root.get("protocolType"), criteria.protocolType()));
        }
        if (criteria.triggerType() != null) {
            spec = spec.and((root, query, cb) -> cb.equal(root.get("triggerType"), criteria.triggerType()));
        }
        if (criteria.status() != null) {
            spec = spec.and((root, query, cb) -> cb.equal(root.get("status"), criteria.status()));
        }
        if (StringUtils.hasText(criteria.interfaceCode())) {
            String value = criteria.interfaceCode().trim();
            spec = spec.and((root, query, cb) -> cb.equal(root.get("interfaceCode"), value));
        }
        if (StringUtils.hasText(criteria.executionIdContains())) {
            String value = "%" + criteria.executionIdContains().trim() + "%";
            spec = spec.and((root, query, cb) -> cb.like(root.get("executionId"), value));
        }
        if (criteria.fromAt() != null) {
            spec = spec.and((root, query, cb) -> cb.greaterThanOrEqualTo(root.get("startedAt"), criteria.fromAt()));
        }
        if (criteria.toAt() != null) {
            spec = spec.and((root, query, cb) -> cb.lessThanOrEqualTo(root.get("startedAt"), criteria.toAt()));
        }
        if (criteria.latencyMin() != null) {
            spec = spec.and((root, query, cb) -> cb.greaterThanOrEqualTo(root.get("latencyMillis"), criteria.latencyMin()));
        }
        if (criteria.latencyMax() != null) {
            spec = spec.and((root, query, cb) -> cb.lessThanOrEqualTo(root.get("latencyMillis"), criteria.latencyMax()));
        }
        if (StringUtils.hasText(criteria.errorCode())) {
            String value = criteria.errorCode().trim();
            spec = spec.and((root, query, cb) -> cb.equal(root.get("errorCode"), value));
        }
        if (StringUtils.hasText(criteria.errorMessageContains())) {
            String value = "%" + criteria.errorMessageContains().trim() + "%";
            spec = spec.and((root, query, cb) -> cb.like(root.get("errorMessage"), value));
        }

        return executionHistoryRepository.findAll(spec, pageable);
    }

    public record ExecutionHistorySearchCriteria(
        String interfaceCode,
        String executionIdContains,
        ProtocolType protocolType,
        TriggerType triggerType,
        ExecutionStatus status,
        LocalDateTime fromAt,
        LocalDateTime toAt,
        Long latencyMin,
        Long latencyMax,
        String errorCode,
        String errorMessageContains
    ) {
    }
}

