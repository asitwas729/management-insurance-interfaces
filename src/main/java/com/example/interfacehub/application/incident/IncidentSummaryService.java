package com.example.interfacehub.application.incident;

import com.example.interfacehub.domain.execution.ExecutionHistory;
import com.example.interfacehub.domain.execution.ExecutionStatus;
import com.example.interfacehub.infrastructure.persistence.ExecutionHistoryRepository;
import com.example.interfacehub.presentation.IncidentSummaryResponse;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class IncidentSummaryService {

    private static final Logger log = LoggerFactory.getLogger(IncidentSummaryService.class);
    private static final String ANTHROPIC_API_URL = "https://api.anthropic.com/v1/messages";
    private static final MediaType JSON = MediaType.get("application/json");

    private final ExecutionHistoryRepository repository;
    private final LlmProperties llmProperties;
    private final ObjectMapper objectMapper;
    private final OkHttpClient httpClient;
    private final ApplicationContext applicationContext;
    private final String anthropicApiUrl;

    @Autowired
    public IncidentSummaryService(
        ExecutionHistoryRepository repository,
        LlmProperties llmProperties,
        ObjectMapper objectMapper,
        ApplicationContext applicationContext
    ) {
        this(repository, llmProperties, objectMapper, applicationContext, new OkHttpClient(), ANTHROPIC_API_URL);
    }

    IncidentSummaryService(
        ExecutionHistoryRepository repository,
        LlmProperties llmProperties,
        ObjectMapper objectMapper,
        ApplicationContext applicationContext,
        OkHttpClient httpClient,
        String anthropicApiUrl
    ) {
        this.repository = repository;
        this.llmProperties = llmProperties;
        this.objectMapper = objectMapper;
        this.applicationContext = applicationContext;
        this.httpClient = httpClient;
        this.anthropicApiUrl = anthropicApiUrl;
    }

    @Transactional(readOnly = true)
    public CompletableFuture<IncidentSummaryResponse> summarize(int hoursBack, int limit) {
        LocalDateTime since = LocalDateTime.now().minusHours(hoursBack);
        List<ExecutionHistory> failures = repository.findRecentByStatus(
            ExecutionStatus.FAILED,
            since,
            PageRequest.of(0, limit)
        );

        if (!llmProperties.isEnabled() || llmProperties.getApiKey().isBlank()) {
            return CompletableFuture.completedFuture(IncidentSummaryResponse.disabled(failures.size(), hoursBack));
        }

        return applicationContext.getBean(IncidentSummaryService.class)
            .callClaude(failures, hoursBack)
            .thenApply(summary -> new IncidentSummaryResponse(summary, failures.size(), hoursBack, LocalDateTime.now()));
    }

    @Async("applicationTaskExecutor")
    public CompletableFuture<String> callClaude(List<ExecutionHistory> failures, int hoursBack) {
        String failureSummaryJson = buildFailureSummaryJson(failures);
        String prompt = buildPrompt(failureSummaryJson, hoursBack, failures.size());

        Map<String, Object> body = Map.of(
            "model", llmProperties.getModel(),
            "max_tokens", llmProperties.getMaxTokens(),
            "messages", List.of(Map.of("role", "user", "content", prompt))
        );

        try {
            String bodyJson = objectMapper.writeValueAsString(body);
            Request request = new Request.Builder()
                .url(anthropicApiUrl)
                .post(RequestBody.create(bodyJson, JSON))
                .addHeader("x-api-key", llmProperties.getApiKey())
                .addHeader("anthropic-version", "2023-06-01")
                .addHeader("content-type", "application/json")
                .build();

            try (Response response = httpClient.newCall(request).execute()) {
                if (!response.isSuccessful() || response.body() == null) {
                    log.error("Claude API error: {}", response.code());
                    return CompletableFuture.completedFuture("LLM API call failed (HTTP " + response.code() + ")");
                }
                Map<?, ?> parsed = objectMapper.readValue(response.body().string(), Map.class);
                List<?> content = (List<?>) parsed.get("content");
                if (content != null && !content.isEmpty()) {
                    Map<?, ?> first = (Map<?, ?>) content.get(0);
                    Object text = first.get("text");
                    return CompletableFuture.completedFuture(text == null ? "LLM response text is empty" : text.toString());
                }
                return CompletableFuture.completedFuture("LLM response parsing failed");
            }
        } catch (Exception e) {
            log.error("Claude API call failed", e);
            return CompletableFuture.completedFuture("LLM call error: " + e.getMessage());
        }
    }

    private String buildFailureSummaryJson(List<ExecutionHistory> failures) {
        Map<String, Map<String, Object>> byInterface = new LinkedHashMap<>();
        for (ExecutionHistory history : failures) {
            byInterface.computeIfAbsent(history.getInterfaceCode(), key -> {
                Map<String, Object> value = new LinkedHashMap<>();
                value.put("count", 0);
                value.put("errorCodes", new LinkedHashMap<String, Integer>());
                return value;
            });
            Map<String, Object> entry = byInterface.get(history.getInterfaceCode());
            entry.put("count", (int) entry.get("count") + 1);
            @SuppressWarnings("unchecked")
            Map<String, Integer> codes = (Map<String, Integer>) entry.get("errorCodes");
            String code = history.getErrorCode() != null ? history.getErrorCode() : "UNKNOWN";
            codes.merge(code, 1, Integer::sum);
        }
        try {
            return objectMapper.writeValueAsString(byInterface);
        } catch (JsonProcessingException e) {
            return "{}";
        }
    }

    private String buildPrompt(String failureSummaryJson, int hoursBack, int count) {
        return String.format("""
            Analyze %d failed insurance interface executions from the last %d hours.
            Summarize operational impact and suggest concrete operator actions in Korean.
            The response must be read-only analysis.

            Failure data:
            %s

            Response format:
            1. Top failing interfaces
            2. Main error patterns
            3. Recommended operator actions
            """, count, hoursBack, failureSummaryJson);
    }
}
