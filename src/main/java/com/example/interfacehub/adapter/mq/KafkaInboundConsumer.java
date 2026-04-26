package com.example.interfacehub.adapter.mq;

import com.example.interfacehub.application.execution.ExecutionOrchestrator;
import com.example.interfacehub.application.mq.DlqMessageService;
import com.example.interfacehub.application.policy.PolicyExecutionContext;
import com.example.interfacehub.domain.execution.TriggerType;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(prefix = "interfacehub.mq", name = "mode", havingValue = "kafka")
public class KafkaInboundConsumer {

    private static final Logger log = LoggerFactory.getLogger(KafkaInboundConsumer.class);
    private static final int MAX_REASON_LENGTH = 500;
    private static final int MAX_IDEMPOTENCY_KEY_LENGTH = 200;

    private final ObjectMapper objectMapper;
    private final DlqMessageService dlqMessageService;
    private final ExecutionOrchestrator executionOrchestrator;
    private final KafkaTemplate<String, String> kafkaTemplate;
    private final KafkaMqProperties properties;

    public KafkaInboundConsumer(
        ObjectMapper objectMapper,
        DlqMessageService dlqMessageService,
        ExecutionOrchestrator executionOrchestrator,
        KafkaTemplate<String, String> kafkaTemplate,
        KafkaMqProperties properties
    ) {
        this.objectMapper = objectMapper;
        this.dlqMessageService = dlqMessageService;
        this.executionOrchestrator = executionOrchestrator;
        this.kafkaTemplate = kafkaTemplate;
        this.properties = properties;
    }

    @KafkaListener(
        topics = "${interfacehub.mq.kafka.consumer-topic:interfacehub.inbound}",
        groupId = "${spring.application.name:interfacehub}-consumer",
        autoStartup = "${interfacehub.mq.kafka.consumer-enabled:false}"
    )
    public void consume(
        String payload,
        @Header(name = "kafka_receivedTopic", required = false) String topic,
        @Header(name = KafkaHeaders.OFFSET, required = false) Long offset,
        @Header(name = KafkaHeaders.RECEIVED_PARTITION, required = false) Integer partition
    ) {
        String actualTopic = topic == null ? properties.getConsumerTopic() : topic;
        String interfaceCode = "UNKNOWN_IF";
        String idempotencyKey = null;
        try {
            JsonNode root = objectMapper.readTree(payload);
            if (!root.isObject()) {
                throw new IllegalArgumentException("Invalid message: root must be a JSON object");
            }
            if (root.path("simulateFailure").asBoolean(false)) {
                throw new IllegalStateException("Simulated Kafka consumer failure");
            }

            interfaceCode = root.path("interfaceCode").asText(null);
            if (interfaceCode == null || interfaceCode.isBlank()) {
                throw new IllegalArgumentException("interfaceCode is required");
            }

            idempotencyKey = resolveIdempotencyKey(root, interfaceCode, partition, offset);

            @SuppressWarnings("unchecked")
            Map<String, Object> payloadMap = parsePayloadMap(root);

            executionOrchestrator.executeByTrigger(
                interfaceCode,
                idempotencyKey,
                payloadMap,
                TriggerType.MQ_INBOUND,
                PolicyExecutionContext.system(null)
            );

            log.info("MQ inbound executed. interfaceCode={}, topic={}, idempotencyKey={}", interfaceCode, actualTopic, idempotencyKey);
        } catch (Exception exception) {
            if (interfaceCode == null || interfaceCode.isBlank() || "UNKNOWN_IF".equals(interfaceCode)) {
                interfaceCode = extractInterfaceCode(payload);
            }
            String reason = buildReason(exception, partition, offset);
            dlqMessageService.save(interfaceCode, actualTopic, payload, reason);
            publishDlq(interfaceCode, actualTopic, payload, idempotencyKey, partition, offset, reason);
            log.warn("MQ inbound failed, moved to DLQ. interfaceCode={}, topic={}", interfaceCode, actualTopic, exception);
        }
    }

    private Map<String, Object> parsePayloadMap(JsonNode root) {
        JsonNode payloadNode = root.get("payload");
        if (payloadNode == null || payloadNode.isNull()) {
            return Map.of();
        }
        if (!payloadNode.isObject()) {
            throw new IllegalArgumentException("Invalid message: payload must be a JSON object");
        }
        return objectMapper.convertValue(payloadNode, new TypeReference<>() {
        });
    }

    private void publishDlq(
        String interfaceCode,
        String originalTopic,
        String payload,
        String idempotencyKey,
        Integer partition,
        Long offset,
        String reason
    ) {
        try {
            Map<String, Object> envelopeMap = new java.util.LinkedHashMap<>();
            envelopeMap.put("interfaceCode", interfaceCode);
            envelopeMap.put("originalTopic", originalTopic);
            envelopeMap.put("payload", payload);
            envelopeMap.put("idempotencyKey", idempotencyKey);
            envelopeMap.put("partition", partition);
            envelopeMap.put("offset", offset);
            envelopeMap.put("reason", reason);
            envelopeMap.put("failedAt", Instant.now().toString());

            String envelope = objectMapper.writeValueAsString(envelopeMap);
            kafkaTemplate.send(properties.getDlqTopic(), interfaceCode, envelope);
        } catch (JsonProcessingException exception) {
            log.warn("Failed to serialize DLQ envelope, interfaceCode={}", interfaceCode, exception);
        } catch (Exception exception) {
            log.warn("Failed to publish to Kafka DLQ topic={}, interfaceCode={}", properties.getDlqTopic(), interfaceCode, exception);
        }
    }

    private String resolveIdempotencyKey(JsonNode root, String interfaceCode, Integer partition, Long offset) {
        String fromMessage = root.path("idempotencyKey").asText(null);
        if (fromMessage != null && !fromMessage.isBlank()) {
            if (fromMessage.length() > MAX_IDEMPOTENCY_KEY_LENGTH) {
                throw new IllegalArgumentException("idempotencyKey is too long (max " + MAX_IDEMPOTENCY_KEY_LENGTH + ")");
            }
            return fromMessage;
        }
        if (partition != null && offset != null) {
            return "MQ-INBOUND-" + interfaceCode + "-" + partition + "-" + offset;
        }
        return "MQ-INBOUND-" + interfaceCode + "-" + UUID.randomUUID().toString().substring(0, 8);
    }

    private String buildReason(Exception exception, Integer partition, Long offset) {
        String base = exception.getClass().getSimpleName() + ": " + (exception.getMessage() == null ? "" : exception.getMessage());
        String meta = "partition=" + (partition == null ? "null" : partition) + ", offset=" + (offset == null ? "null" : offset);
        String combined = base + " (" + meta + ")";
        if (combined.length() <= MAX_REASON_LENGTH) {
            return combined;
        }
        return combined.substring(0, MAX_REASON_LENGTH);
    }

    private String extractInterfaceCode(String payload) {
        try {
            return objectMapper.readTree(payload).path("interfaceCode").asText("UNKNOWN_IF");
        } catch (Exception exception) {
            return "UNKNOWN_IF";
        }
    }
}
