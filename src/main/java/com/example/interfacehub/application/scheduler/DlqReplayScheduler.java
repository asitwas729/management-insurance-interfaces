package com.example.interfacehub.application.scheduler;

import com.example.interfacehub.application.mq.DlqReplayService;
import com.example.interfacehub.common.error.BusinessException;
import com.example.interfacehub.domain.mq.DlqReplayRequest;
import com.example.interfacehub.domain.mq.DlqReplayStatus;
import com.example.interfacehub.infrastructure.persistence.DlqReplayRequestRepository;
import com.example.interfacehub.presentation.ExecuteDlqReplayRequest;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(name = "interfacehub.scheduler.enabled", havingValue = "true", matchIfMissing = true)
public class DlqReplayScheduler {

    private static final Logger log = LoggerFactory.getLogger(DlqReplayScheduler.class);
    private static final ExecuteDlqReplayRequest SYSTEM_EXECUTOR = new ExecuteDlqReplayRequest("system");

    private final DlqReplayService dlqReplayService;
    private final DlqReplayRequestRepository dlqReplayRequestRepository;

    public DlqReplayScheduler(
        DlqReplayService dlqReplayService,
        DlqReplayRequestRepository dlqReplayRequestRepository
    ) {
        this.dlqReplayService = dlqReplayService;
        this.dlqReplayRequestRepository = dlqReplayRequestRepository;
    }

    @Scheduled(fixedDelayString = "#{${interfacehub.scheduler.dlq-fixed-delay-seconds:60} * 1000}")
    public void executeApprovedReplays() {
        List<DlqReplayRequest> approved = dlqReplayRequestRepository.findByStatusOrderByCreatedAtDesc(DlqReplayStatus.APPROVED);
        if (approved.isEmpty()) {
            return;
        }
        log.info("[DlqReplayScheduler] Found {} APPROVED DLQ replay request(s) — executing", approved.size());

        for (DlqReplayRequest request : approved) {
            try {
                dlqReplayService.execute(request.getId(), SYSTEM_EXECUTOR);
                log.info("[DlqReplayScheduler] DLQ replay request {} executed successfully", request.getId());
            } catch (BusinessException e) {
                log.warn("[DlqReplayScheduler] DLQ replay request {} skipped: {}", request.getId(), e.getMessage());
            } catch (Exception e) {
                log.error("[DlqReplayScheduler] DLQ replay request {} threw unexpected error", request.getId(), e);
            }
        }
    }
}
