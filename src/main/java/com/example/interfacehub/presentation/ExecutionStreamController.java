package com.example.interfacehub.presentation;

import com.example.interfacehub.application.execution.ExecutionCompletedEvent;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import org.springframework.context.event.EventListener;
import org.springframework.http.MediaType;
import org.springframework.scheduling.annotation.Async;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

@RestController
@RequestMapping("/api/v1/interfaces/{interfaceCode}/executions/stream")
@Tag(name = "Execution", description = "SSE streaming for execution status")
public class ExecutionStreamController {

    private final Map<String, CopyOnWriteArrayList<SseEmitter>> emitters = new ConcurrentHashMap<>();

    @GetMapping(produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter stream(@PathVariable String interfaceCode) {
        SseEmitter emitter = new SseEmitter(120_000L);
        CopyOnWriteArrayList<SseEmitter> list = emitters.computeIfAbsent(interfaceCode, ignored -> new CopyOnWriteArrayList<>());
        list.add(emitter);

        Runnable remove = () -> {
            CopyOnWriteArrayList<SseEmitter> current = emitters.get(interfaceCode);
            if (current == null) {
                return;
            }
            current.remove(emitter);
            if (current.isEmpty()) {
                emitters.remove(interfaceCode, current);
            }
        };
        emitter.onCompletion(remove);
        emitter.onTimeout(remove);
        emitter.onError(error -> remove.run());
        return emitter;
    }

    @Async("applicationTaskExecutor")
    @EventListener
    public void onExecutionCompleted(ExecutionCompletedEvent event) {
        CopyOnWriteArrayList<SseEmitter> targets = emitters.get(event.getInterfaceCode());
        if (targets == null || targets.isEmpty()) {
            return;
        }

        Map<String, Object> payload = Map.of(
            "executionId", event.getExecutionId(),
            "status", event.getStatus(),
            "errorCode", event.getErrorCode() == null ? "" : event.getErrorCode(),
            "latencyMillis", event.getLatencyMillis()
        );

        List<SseEmitter> dead = new ArrayList<>();
        for (SseEmitter emitter : targets) {
            try {
                emitter.send(SseEmitter.event().name("execution-completed").data(payload));
            } catch (IOException exception) {
                dead.add(emitter);
            }
        }
        if (!dead.isEmpty()) {
            targets.removeAll(dead);
            if (targets.isEmpty()) {
                emitters.remove(event.getInterfaceCode(), targets);
            }
        }
    }
}
