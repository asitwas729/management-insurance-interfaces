package com.example.interfacehub;

import com.example.interfacehub.adapter.mq.KafkaMqProperties;
import com.example.interfacehub.application.mq.DlqReplayPolicyProperties;
import com.example.interfacehub.application.notification.NotificationProperties;
import com.example.interfacehub.application.scheduler.RetentionProperties;
import com.example.interfacehub.application.scheduler.SchedulerProperties;
import com.example.interfacehub.infrastructure.security.JwtProperties;
import com.example.interfacehub.infrastructure.seed.SeedProperties;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.scheduling.annotation.EnableAsync;

@SpringBootApplication
@EnableCaching
@EnableAsync
@EnableConfigurationProperties({KafkaMqProperties.class, DlqReplayPolicyProperties.class, JwtProperties.class, SchedulerProperties.class, NotificationProperties.class, RetentionProperties.class, SeedProperties.class})
public class InterfaceHubApplication {

    public static void main(String[] args) {
        SpringApplication.run(InterfaceHubApplication.class, args);
    }
}
