package com.example.interfacehub.adapter.mq;

public interface MqGateway {

    MqProcessResult publish(String interfaceCode, String topic, String payload);
}
