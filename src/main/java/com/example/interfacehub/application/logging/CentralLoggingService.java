package com.example.interfacehub.application.logging;

import com.example.interfacehub.domain.logging.ApiRequestLog;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.LinkedHashMap;
import java.util.Map;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

@Service
public class CentralLoggingService {

    private static final Logger log = LoggerFactory.getLogger(CentralLoggingService.class);
    private static final MediaType JSON = MediaType.get("application/json");

    private final CentralLoggingProperties properties;
    private final ObjectMapper objectMapper;
    private final OkHttpClient httpClient;

    public CentralLoggingService(CentralLoggingProperties properties, ObjectMapper objectMapper) {
        this.properties = properties;
        this.objectMapper = objectMapper;
        this.httpClient = new OkHttpClient();
    }

    @Async("applicationTaskExecutor")
    public void sendApiRequestLog(ApiRequestLog entry) {
        if (!properties.isEnabled() || properties.getWebhookUrl().isBlank()) {
            return;
        }

        try {
            Map<String, Object> payload = new LinkedHashMap<>();
            payload.put("type", "API_REQUEST_LOG");
            payload.put("id", entry.getId());
            payload.put("actor", entry.getActor());
            payload.put("source", entry.getSource());
            payload.put("method", entry.getMethod());
            payload.put("path", entry.getPath());
            payload.put("requestBody", entry.getRequestBody());
            payload.put("responseStatus", entry.getResponseStatus());
            payload.put("responseBody", entry.getResponseBody());
            payload.put("durationMs", entry.getDurationMs());
            payload.put("errorMessage", entry.getErrorMessage());
            payload.put("occurredAt", entry.getOccurredAt().toString());

            String body = objectMapper.writeValueAsString(payload);
            Request request = new Request.Builder()
                .url(properties.getWebhookUrl())
                .post(RequestBody.create(body, JSON))
                .addHeader("content-type", "application/json")
                .build();
            try (Response response = httpClient.newCall(request).execute()) {
                if (!response.isSuccessful()) {
                    log.warn("[CentralLogging] webhook returned HTTP {}", response.code());
                }
            }
        } catch (Exception e) {
            log.error("[CentralLogging] send failed", e);
        }
    }
}
