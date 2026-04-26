package com.example.interfacehub.application.execution;

import com.example.interfacehub.domain.execution.ExecutionHistory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;

@Component
public class ExecutionEventPublisher {

    private final ApplicationEventPublisher publisher;

    public ExecutionEventPublisher(ApplicationEventPublisher publisher) {
        this.publisher = publisher;
    }

    public void publish(ExecutionHistory history) {
        if (history == null) {
            return;
        }
        publisher.publishEvent(new ExecutionCompletedEvent(
            this,
            history.getInterfaceCode(),
            history.getExecutionId(),
            history.getStatus().name(),
            history.getErrorCode(),
            history.getLatencyMillis() != null ? history.getLatencyMillis() : 0L
        ));
    }
}

