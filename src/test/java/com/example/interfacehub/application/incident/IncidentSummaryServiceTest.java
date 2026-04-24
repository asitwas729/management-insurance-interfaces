package com.example.interfacehub.application.incident;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import com.example.interfacehub.domain.execution.ExecutionHistory;
import com.example.interfacehub.domain.execution.ExecutionStatus;
import com.example.interfacehub.domain.execution.TriggerType;
import com.example.interfacehub.domain.interfaceconfig.ProtocolType;
import com.example.interfacehub.infrastructure.persistence.ExecutionHistoryRepository;
import com.example.interfacehub.infrastructure.persistence.IncidentSummaryRepository;
import com.example.interfacehub.presentation.IncidentSummaryResponse;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.LocalDateTime;
import java.util.List;
import okhttp3.Call;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Protocol;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.ResponseBody;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationContext;
import org.springframework.data.domain.Pageable;

@ExtendWith(MockitoExtension.class)
class IncidentSummaryServiceTest {

    @Mock
    private ExecutionHistoryRepository repository;

    @Mock
    private IncidentSummaryRepository summaryRepository;

    @Mock
    private ApplicationContext applicationContext;

    @Mock
    private OkHttpClient httpClient;

    @Mock
    private Call call;

    private LlmProperties properties;
    private IncidentSummaryService service;

    @BeforeEach
    void setUp() {
        properties = new LlmProperties();
        service = new IncidentSummaryService(
            repository,
            summaryRepository,
            properties,
            new ObjectMapper(),
            applicationContext,
            httpClient,
            "https://example.test/v1/messages"
        );
    }

    @Test
    void summarize_returns_fallback_when_llm_is_disabled() {
        when(repository.findRecentByStatus(any(ExecutionStatus.class), any(LocalDateTime.class), any(Pageable.class)))
            .thenReturn(List.of(failedHistory()));

        IncidentSummaryResponse response = service.summarize(24, 100).join();

        assertThat(response.analyzedCount()).isEqualTo(1);
        assertThat(response.summary()).contains("disabled");
    }

    @Test
    void call_claude_returns_text_when_api_succeeds() throws Exception {
        properties.setEnabled(true);
        properties.setApiKey("test-key");
        when(httpClient.newCall(any(Request.class))).thenReturn(call);
        when(call.execute()).thenReturn(response(200, """
            {
              "content": [
                { "type": "text", "text": "주요 실패는 MQ_IF에 집중되었습니다." }
              ]
            }
            """));

        String summary = service.callClaude(List.of(failedHistory()), 24).join();

        assertThat(summary).contains("MQ_IF");
    }

    @Test
    void call_claude_returns_fallback_text_when_api_fails() throws Exception {
        properties.setEnabled(true);
        properties.setApiKey("test-key");
        when(httpClient.newCall(any(Request.class))).thenReturn(call);
        when(call.execute()).thenReturn(response(500, "{\"error\":\"server\"}"));

        String summary = service.callClaude(List.of(failedHistory()), 24).join();

        assertThat(summary).contains("HTTP 500");
    }

    private ExecutionHistory failedHistory() {
        ExecutionHistory history = ExecutionHistory.start(
            "EXEC-1",
            "MQ_IF",
            ProtocolType.MQ,
            TriggerType.MANUAL,
            "{}"
        );
        history.markFailed("MQ_CONSUME_FAILED", "temporary broker failure", 1200L);
        return history;
    }

    private Response response(int code, String body) {
        return new Response.Builder()
            .request(new Request.Builder().url("https://example.test/v1/messages").build())
            .protocol(Protocol.HTTP_1_1)
            .code(code)
            .message("test")
            .body(ResponseBody.create(body, MediaType.get("application/json")))
            .build();
    }
}
