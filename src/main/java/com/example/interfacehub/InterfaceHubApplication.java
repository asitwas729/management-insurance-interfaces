package com.example.interfacehub;

import com.example.interfacehub.adapter.mq.KafkaMqProperties;
import com.example.interfacehub.application.mq.DlqReplayPolicyProperties;
import com.example.interfacehub.infrastructure.security.JwtProperties;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;

@SpringBootApplication
@EnableConfigurationProperties({KafkaMqProperties.class, DlqReplayPolicyProperties.class, JwtProperties.class})
public class InterfaceHubApplication {

    public static void main(String[] args) {
        SpringApplication.run(InterfaceHubApplication.class, args);
    }
}
