package com.example.interfacehub.adapter.mq;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.example.interfacehub.application.execution.ExecutionOrchestrator;
import com.example.interfacehub.application.mq.DlqMessageService;
import com.example.interfacehub.application.policy.PolicyExecutionContext;
import com.example.interfacehub.domain.execution.TriggerType;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.kafka.core.KafkaTemplate;

@ExtendWith(MockitoExtension.class)
class KafkaInboundConsumerTest {

    @Mock
    private DlqMessageService dlqMessageService;

    @Mock
    private ExecutionOrchestrator executionOrchestrator;

    @Mock
    private KafkaTemplate<String, String> kafkaTemplate;

    private KafkaInboundConsumer consumer;
    private ObjectMapper objectMapper;
    private KafkaMqProperties properties;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();
        properties = new KafkaMqProperties();
        properties.setDlqTopic("interfacehub.dlq");
        properties.setConsumerTopic("interfacehub.inbound");

        consumer = new KafkaInboundConsumer(
            objectMapper,
            dlqMessageService,
            executionOrchestrator,
            kafkaTemplate,
            properties
        );
    }

    @Test
    void consume_success_should_execute_orchestrator_with_partition_offset_idempotency_key() {
        String payload = """
            {"interfaceCode":"IF001","payload":{"key":"value"}}
            """;

        consumer.consume(payload, null, 10L, 1);

        @SuppressWarnings("unchecked")
        ArgumentCaptor<Map<String, Object>> payloadCaptor = ArgumentCaptor.forClass(Map.class);
        ArgumentCaptor<String> idempotencyKeyCaptor = ArgumentCaptor.forClass(String.class);

        verify(executionOrchestrator).executeByTrigger(
            eq("IF001"),
            idempotencyKeyCaptor.capture(),
            payloadCaptor.capture(),
            eq(TriggerType.MQ_INBOUND),
            eq(PolicyExecutionContext.system(null))
        );
        assertThat(idempotencyKeyCaptor.getValue()).isEqualTo("MQ-INBOUND-IF001-1-10");
        assertThat(payloadCaptor.getValue()).containsEntry("key", "value");

        verify(dlqMessageService, never()).save(anyString(), anyString(), anyString(), anyString());
    }

    @Test
    void consume_should_prefer_message_idempotency_key() {
        String payload = """
            {"interfaceCode":"IF001","idempotencyKey":"client-123","payload":{"k":1}}
            """;

        consumer.consume(payload, "topic.override", 99L, 2);

        ArgumentCaptor<String> idempotencyKeyCaptor = ArgumentCaptor.forClass(String.class);
        verify(executionOrchestrator).executeByTrigger(
            eq("IF001"),
            idempotencyKeyCaptor.capture(),
            any(),
            eq(TriggerType.MQ_INBOUND),
            any()
        );
        assertThat(idempotencyKeyCaptor.getValue()).isEqualTo("client-123");
    }

    @Test
    void consume_missing_interface_code_should_move_to_dlq_and_not_execute() throws Exception {
        String payload = """
            {"payload":{"a":1}}
            """;

        consumer.consume(payload, null, 10L, 1);

        verify(executionOrchestrator, never()).executeByTrigger(anyString(), anyString(), any(), any(), any());

        ArgumentCaptor<String> reasonCaptor = ArgumentCaptor.forClass(String.class);
        verify(dlqMessageService).save(eq("UNKNOWN_IF"), eq("interfacehub.inbound"), eq(payload), reasonCaptor.capture());
        assertThat(reasonCaptor.getValue()).contains("interfaceCode is required");
        assertThat(reasonCaptor.getValue()).contains("partition=1").contains("offset=10");

        ArgumentCaptor<String> envelopeCaptor = ArgumentCaptor.forClass(String.class);
        verify(kafkaTemplate).send(eq("interfacehub.dlq"), eq("UNKNOWN_IF"), envelopeCaptor.capture());

        Map<String, Object> envelope = objectMapper.readValue(envelopeCaptor.getValue(), new TypeReference<>() {
        });
        assertThat(envelope.get("interfaceCode")).isEqualTo("UNKNOWN_IF");
        assertThat(envelope.get("originalTopic")).isEqualTo("interfacehub.inbound");
        assertThat(envelope.get("partition")).isEqualTo(1);
        assertThat(envelope.get("offset")).isEqualTo(10);
    }

    @Test
    void consume_payload_not_object_should_move_to_dlq() {
        String payload = """
            {"interfaceCode":"IF001","payload":"not-an-object"}
            """;

        consumer.consume(payload, null, 10L, 1);

        verify(executionOrchestrator, never()).executeByTrigger(anyString(), anyString(), any(), any(), any());
        verify(dlqMessageService).save(eq("IF001"), eq("interfacehub.inbound"), eq(payload), anyString());
    }
}
