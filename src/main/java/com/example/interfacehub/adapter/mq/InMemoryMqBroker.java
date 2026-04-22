package com.example.interfacehub.adapter.mq;

import com.example.interfacehub.application.mq.DlqMessageService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(prefix = "interfacehub.mq", name = "mode", havingValue = "in-memory", matchIfMissing = true)
public class InMemoryMqBroker implements MqGateway {

    private final ObjectMapper objectMapper;
    private final DlqMessageService dlqMessageService;

    public InMemoryMqBroker(ObjectMapper objectMapper, DlqMessageService dlqMessageService) {
        this.objectMapper = objectMapper;
        this.dlqMessageService = dlqMessageService;
    }

    @Override
    public MqProcessResult publish(String interfaceCode, String topic, String payload) {
        try {
            JsonNode root = objectMapper.readTree(payload);
            if (root.path("simulateFailure").asBoolean(false)) {
                throw new IllegalStateException("Simulated MQ consumer failure");
            }
            String response = "{\"result\":\"MQ_ACCEPTED\",\"topic\":\"" + topic + "\"}";
            return MqProcessResult.success(response);
        } catch (Exception exception) {
            dlqMessageService.save(interfaceCode, topic, payload, exception.getMessage());
            return MqProcessResult.failure(exception.getMessage());
        }
    }
}
