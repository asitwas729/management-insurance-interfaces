package com.example.interfacehub.application.search;

import com.example.interfacehub.infrastructure.persistence.ExecutionHistoryRepository;
import com.example.interfacehub.infrastructure.persistence.InterfaceConfigVersionRepository;
import com.example.interfacehub.infrastructure.persistence.InterfaceDefinitionRepository;
import com.example.interfacehub.infrastructure.persistence.RetryTaskRepository;
import com.example.interfacehub.presentation.GlobalSearchResponse;
import com.example.interfacehub.presentation.GlobalSearchResponse.SearchItem;
import java.util.ArrayList;
import java.util.List;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class GlobalSearchService {

    private final InterfaceDefinitionRepository interfaceDefinitionRepository;
    private final InterfaceConfigVersionRepository interfaceConfigVersionRepository;
    private final ExecutionHistoryRepository executionHistoryRepository;
    private final RetryTaskRepository retryTaskRepository;

    public GlobalSearchService(
        InterfaceDefinitionRepository interfaceDefinitionRepository,
        InterfaceConfigVersionRepository interfaceConfigVersionRepository,
        ExecutionHistoryRepository executionHistoryRepository,
        RetryTaskRepository retryTaskRepository
    ) {
        this.interfaceDefinitionRepository = interfaceDefinitionRepository;
        this.interfaceConfigVersionRepository = interfaceConfigVersionRepository;
        this.executionHistoryRepository = executionHistoryRepository;
        this.retryTaskRepository = retryTaskRepository;
    }

    public GlobalSearchResponse search(String q, int limit) {
        String query = q == null ? "" : q.trim();
        if (query.isBlank()) {
            return new GlobalSearchResponse(List.of());
        }

        int safeLimit = Math.max(1, Math.min(50, limit));
        PageRequest page = PageRequest.of(0, safeLimit);

        List<SearchItem> results = new ArrayList<>();

        interfaceDefinitionRepository.search(query, page).forEach((d) -> results.add(
            SearchItem.interfaceItem(
                d.getInterfaceCode(),
                d.getName(),
                d.getProtocolType() == null ? "" : d.getProtocolType().name(),
                d.getStatus() == null ? "" : d.getStatus().name()
            )
        ));

        interfaceConfigVersionRepository.search(query, page).forEach((c) -> results.add(
            SearchItem.configItem(
                c.getId(),
                c.getInterfaceDefinition() == null ? "" : c.getInterfaceDefinition().getInterfaceCode(),
                c.getVersion(),
                c.getEnvironment() == null ? "" : c.getEnvironment().name(),
                c.getEndpoint()
            )
        ));

        executionHistoryRepository.search(query, page).forEach((e) -> results.add(
            SearchItem.historyItem(
                e.getInterfaceCode(),
                e.getExecutionId(),
                e.getStatus() == null ? "" : e.getStatus().name(),
                e.getErrorCode(),
                e.getErrorMessage()
            )
        ));

        retryTaskRepository.search(query, page).forEach((r) -> results.add(
            SearchItem.retryItem(
                r.getId(),
                r.getInterfaceDefinition() == null ? "" : r.getInterfaceDefinition().getInterfaceCode(),
                r.getOriginalExecutionId(),
                r.getStatus() == null ? "" : r.getStatus().name(),
                r.getRequester()
            )
        ));

        return new GlobalSearchResponse(results);
    }
}

