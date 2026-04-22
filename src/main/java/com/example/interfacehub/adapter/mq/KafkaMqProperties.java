package com.example.interfacehub.adapter.mq;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "interfacehub.mq.kafka")
public class KafkaMqProperties {

    private String dlqTopic = "interfacehub.dlq";
    private String consumerTopic = "interfacehub.inbound";
    private boolean consumerEnabled;

    public String getDlqTopic() {
        return dlqTopic;
    }

    public void setDlqTopic(String dlqTopic) {
        this.dlqTopic = dlqTopic;
    }

    public String getConsumerTopic() {
        return consumerTopic;
    }

    public void setConsumerTopic(String consumerTopic) {
        this.consumerTopic = consumerTopic;
    }

    public boolean isConsumerEnabled() {
        return consumerEnabled;
    }

    public void setConsumerEnabled(boolean consumerEnabled) {
        this.consumerEnabled = consumerEnabled;
    }
}
