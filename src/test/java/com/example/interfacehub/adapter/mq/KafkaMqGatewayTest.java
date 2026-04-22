package com.example.interfacehub.adapter.mq;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.example.interfacehub.application.mq.DlqMessageService;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.concurrent.CompletableFuture;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;

@ExtendWith(MockitoExtension.class)
class KafkaMqGatewayTest {

    @Mock
    private KafkaTemplate<String, String> kafkaTemplate;

    @Mock
    private DlqMessageService dlqMessageService;

    private KafkaMqGateway kafkaMqGateway;

    @BeforeEach
    void setUp() {
        KafkaMqProperties properties = new KafkaMqProperties();
        properties.setDlqTopic("interfacehub.dlq");
        kafkaMqGateway = new KafkaMqGateway(kafkaTemplate, dlqMessageService, new ObjectMapper(), properties);
    }

    @Test
    void publish_success() {
        CompletableFuture<SendResult<String, String>> future = CompletableFuture.completedFuture(null);
        when(kafkaTemplate.send(anyString(), anyString(), anyString())).thenReturn(future);

        MqProcessResult result = kafkaMqGateway.publish("IF1", "topic.test", "{\"a\":1}");

        assertThat(result.success()).isTrue();
        assertThat(result.responsePayload()).contains("MQ_ACCEPTED");
    }

    @Test
    void publish_failure_should_save_dlq() {
        doThrow(new RuntimeException("kafka unavailable"))
            .when(kafkaTemplate).send(anyString(), anyString(), anyString());

        MqProcessResult result = kafkaMqGateway.publish("IF1", "topic.test", "{\"a\":1}");

        assertThat(result.success()).isFalse();
        verify(dlqMessageService).save("IF1", "topic.test", "{\"a\":1}", "kafka unavailable");
    }
}
