package com.example.interfacehub.adapter.mq;

import com.example.interfacehub.application.mq.DlqMessageService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(prefix = "interfacehub.mq", name = "mode", havingValue = "kafka")
public class KafkaInboundConsumer {

    private static final Logger log = LoggerFactory.getLogger(KafkaInboundConsumer.class);

    private final ObjectMapper objectMapper;
    private final DlqMessageService dlqMessageService;
    private final KafkaTemplate<String, String> kafkaTemplate;
    private final KafkaMqProperties properties;

    public KafkaInboundConsumer(
        ObjectMapper objectMapper,
        DlqMessageService dlqMessageService,
        KafkaTemplate<String, String> kafkaTemplate,
        KafkaMqProperties properties
    ) {
        this.objectMapper = objectMapper;
        this.dlqMessageService = dlqMessageService;
        this.kafkaTemplate = kafkaTemplate;
        this.properties = properties;
    }

    @KafkaListener(
        topics = "${interfacehub.mq.kafka.consumer-topic:interfacehub.inbound}",
        groupId = "${spring.application.name:interfacehub}-consumer",
        autoStartup = "${interfacehub.mq.kafka.consumer-enabled:false}"
    )
    public void consume(String payload, @Header(name = "kafka_receivedTopic", required = false) String topic) {
        String actualTopic = topic == null ? properties.getConsumerTopic() : topic;
        try {
            JsonNode root = objectMapper.readTree(payload);
            if (root.path("simulateFailure").asBoolean(false)) {
                throw new IllegalStateException("Simulated Kafka consumer failure");
            }
            log.info("Kafka message consumed successfully. topic={}", actualTopic);
        } catch (Exception exception) {
            String interfaceCode = extractInterfaceCode(payload);
            dlqMessageService.save(interfaceCode, actualTopic, payload, exception.getMessage());
            publishDlq(interfaceCode, payload, exception.getMessage());
            log.warn("Kafka consumer failed. moved to DLQ. topic={}, interfaceCode={}", actualTopic, interfaceCode, exception);
        }
    }

    private void publishDlq(String interfaceCode, String payload, String reason) {
        String envelope = "{\"interfaceCode\":\"%s\",\"payload\":%s,\"reason\":\"%s\"}"
            .formatted(interfaceCode, quoteJson(payload), reason == null ? "" : reason.replace("\"", "'"));
        kafkaTemplate.send(properties.getDlqTopic(), interfaceCode, envelope);
    }

    private String extractInterfaceCode(String payload) {
        try {
            return objectMapper.readTree(payload).path("interfaceCode").asText("UNKNOWN_IF");
        } catch (Exception exception) {
            return "UNKNOWN_IF";
        }
    }

    private String quoteJson(String payload) {
        return "\"" + payload.replace("\\", "\\\\").replace("\"", "\\\"") + "\"";
    }
}
