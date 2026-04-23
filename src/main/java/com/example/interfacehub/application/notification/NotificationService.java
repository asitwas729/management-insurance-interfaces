package com.example.interfacehub.application.notification;

import com.fasterxml.jackson.databind.ObjectMapper;
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
public class NotificationService {

    private static final Logger log = LoggerFactory.getLogger(NotificationService.class);
    private static final MediaType JSON = MediaType.get("application/json");

    private final NotificationProperties properties;
    private final ObjectMapper objectMapper;
    private final OkHttpClient httpClient;

    public NotificationService(NotificationProperties properties, ObjectMapper objectMapper) {
        this.properties = properties;
        this.objectMapper = objectMapper;
        this.httpClient = new OkHttpClient();
    }

    @Async("applicationTaskExecutor")
    public void sendSlaBreachAlert(String interfaceCode, long latencyMillis, long slaMillis) {
        if (!properties.isEnabled() || properties.getWebhookUrl().isBlank()) {
            log.warn("[SLA] Breach detected — interfaceCode={}, latency={}ms, sla={}ms (webhook disabled)",
                interfaceCode, latencyMillis, slaMillis);
            return;
        }
        Map<String, Object> payload = Map.of(
            "type", "SLA_BREACH",
            "interfaceCode", interfaceCode,
            "latencyMillis", latencyMillis,
            "slaMillis", slaMillis,
            "excessMillis", latencyMillis - slaMillis
        );
        sendWebhook(payload);
    }

    private void sendWebhook(Map<String, Object> payload) {
        try {
            String body = objectMapper.writeValueAsString(payload);
            Request request = new Request.Builder()
                .url(properties.getWebhookUrl())
                .post(RequestBody.create(body, JSON))
                .addHeader("content-type", "application/json")
                .build();
            try (Response response = httpClient.newCall(request).execute()) {
                if (!response.isSuccessful()) {
                    log.warn("[Notification] Webhook returned HTTP {}", response.code());
                }
            }
        } catch (Exception e) {
            log.error("[Notification] Webhook call failed", e);
        }
    }
}
