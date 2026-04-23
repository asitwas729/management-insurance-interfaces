package com.example.interfacehub.application.scheduler;

import com.example.interfacehub.application.execution.ExecutionOrchestrator;
import com.example.interfacehub.common.error.BusinessException;
import com.example.interfacehub.domain.execution.TriggerType;
import com.example.interfacehub.domain.scheduler.InterfaceSchedule;
import com.example.interfacehub.infrastructure.persistence.InterfaceScheduleRepository;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.PostConstruct;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ScheduledFuture;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.scheduling.support.CronTrigger;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(name = "interfacehub.scheduler.enabled", havingValue = "true", matchIfMissing = true)
public class InterfaceJobScheduler {

    private static final Logger log = LoggerFactory.getLogger(InterfaceJobScheduler.class);

    private final InterfaceScheduleRepository scheduleRepository;
    private final ExecutionOrchestrator executionOrchestrator;
    private final ObjectMapper objectMapper;
    private final TaskScheduler taskScheduler;
    private final ConcurrentHashMap<String, ScheduledFuture<?>> activeTasks = new ConcurrentHashMap<>();

    public InterfaceJobScheduler(
        InterfaceScheduleRepository scheduleRepository,
        ExecutionOrchestrator executionOrchestrator,
        ObjectMapper objectMapper,
        TaskScheduler taskScheduler
    ) {
        this.scheduleRepository = scheduleRepository;
        this.executionOrchestrator = executionOrchestrator;
        this.objectMapper = objectMapper;
        this.taskScheduler = taskScheduler;
    }

    @PostConstruct
    public void loadSchedules() {
        List<InterfaceSchedule> schedules = scheduleRepository.findByEnabledTrue();
        for (InterfaceSchedule schedule : schedules) {
            register(schedule);
        }
        log.info("[InterfaceJobScheduler] Loaded {} interface schedule(s)", schedules.size());
    }

    public void register(InterfaceSchedule schedule) {
        cancelIfPresent(schedule.getInterfaceCode());
        ScheduledFuture<?> future = taskScheduler.schedule(
            () -> triggerInterface(schedule.getInterfaceCode(), schedule.getPayloadTemplate()),
            new CronTrigger(schedule.getCronExpression())
        );
        activeTasks.put(schedule.getInterfaceCode(), future);
        log.info("[InterfaceJobScheduler] Registered schedule for '{}' with cron '{}'",
            schedule.getInterfaceCode(), schedule.getCronExpression());
    }

    public void cancel(String interfaceCode) {
        cancelIfPresent(interfaceCode);
        log.info("[InterfaceJobScheduler] Cancelled schedule for '{}'", interfaceCode);
    }

    private void cancelIfPresent(String interfaceCode) {
        ScheduledFuture<?> existing = activeTasks.remove(interfaceCode);
        if (existing != null) {
            existing.cancel(false);
        }
    }

    private void triggerInterface(String interfaceCode, String payloadTemplate) {
        try {
            Map<String, Object> payload = parsePayloadTemplate(payloadTemplate);
            String idempotencyKey = "SCHEDULED-" + interfaceCode + "-" + UUID.randomUUID().toString().substring(0, 8);
            executionOrchestrator.executeByTrigger(interfaceCode, idempotencyKey, payload, TriggerType.SCHEDULED);
            log.info("[InterfaceJobScheduler] Scheduled execution triggered for '{}'", interfaceCode);
        } catch (BusinessException e) {
            log.warn("[InterfaceJobScheduler] Scheduled execution for '{}' failed: {}", interfaceCode, e.getMessage());
        } catch (Exception e) {
            log.error("[InterfaceJobScheduler] Scheduled execution for '{}' threw unexpected error", interfaceCode, e);
        }
    }

    private Map<String, Object> parsePayloadTemplate(String payloadTemplate) {
        if (payloadTemplate == null || payloadTemplate.isBlank()) {
            return Map.of();
        }
        try {
            return objectMapper.readValue(payloadTemplate, new TypeReference<>() {});
        } catch (JsonProcessingException e) {
            log.warn("[InterfaceJobScheduler] Invalid payloadTemplate JSON, using empty payload");
            return Map.of();
        }
    }
}
