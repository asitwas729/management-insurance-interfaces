package com.example.interfacehub.adapter.mq;

import com.example.interfacehub.application.mq.DlqMessageService;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(prefix = "interfacehub.mq", name = "mode", havingValue = "kafka")
public class KafkaMqGateway implements MqGateway {

    private static final Logger log = LoggerFactory.getLogger(KafkaMqGateway.class);

    private final KafkaTemplate<String, String> kafkaTemplate;
    private final DlqMessageService dlqMessageService;
    private final ObjectMapper objectMapper;
    private final String dlqTopic;

    public KafkaMqGateway(
        KafkaTemplate<String, String> kafkaTemplate,
        DlqMessageService dlqMessageService,
        ObjectMapper objectMapper,
        KafkaMqProperties properties
    ) {
        this.kafkaTemplate = kafkaTemplate;
        this.dlqMessageService = dlqMessageService;
        this.objectMapper = objectMapper;
        this.dlqTopic = properties.getDlqTopic();
    }

    @Override
    public MqProcessResult publish(String interfaceCode, String topic, String payload) {
        try {
            kafkaTemplate.send(topic, interfaceCode, payload).get(3, TimeUnit.SECONDS);
            String response = "{\"result\":\"MQ_ACCEPTED\",\"topic\":\"" + topic + "\"}";
            return MqProcessResult.success(response);
        } catch (Exception exception) {
            String reason = exception.getMessage();
            dlqMessageService.save(interfaceCode, topic, payload, reason);
            publishToKafkaDlq(interfaceCode, topic, payload, reason);
            return MqProcessResult.failure(reason);
        }
    }

    private void publishToKafkaDlq(String interfaceCode, String originalTopic, String payload, String reason) {
        try {
            String envelope = objectMapper.writeValueAsString(Map.of(
                "interfaceCode", interfaceCode,
                "originalTopic", originalTopic,
                "payload", payload,
                "reason", reason,
                "failedAt", Instant.now().toString()
            ));
            kafkaTemplate.send(dlqTopic, interfaceCode, envelope);
        } catch (JsonProcessingException exception) {
            log.warn("Failed to serialize DLQ envelope, interfaceCode={}", interfaceCode, exception);
        } catch (Exception exception) {
            log.warn("Failed to publish to Kafka DLQ topic={}, interfaceCode={}", dlqTopic, interfaceCode, exception);
        }
    }
}
