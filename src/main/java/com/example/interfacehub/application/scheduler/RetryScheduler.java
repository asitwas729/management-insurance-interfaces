package com.example.interfacehub.application.scheduler;

import com.example.interfacehub.application.retry.RetryTaskService;
import com.example.interfacehub.common.error.BusinessException;
import com.example.interfacehub.domain.retry.RetryStatus;
import com.example.interfacehub.domain.retry.RetryTask;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import lombok.RequiredArgsConstructor;

//@Component
//@ConditionalOnProperty(name = "interfacehub.scheduler.enabled", havingValue = "true", matchIfMissing = true)
@Component
@ConditionalOnProperty(
        name = "interfacehub.scheduler.retry.enabled",
        havingValue = "true",
        matchIfMissing = false
)
public class RetryScheduler {

    private static final Logger log = LoggerFactory.getLogger(RetryScheduler.class);

    private final RetryTaskService retryTaskService;

    public RetryScheduler(RetryTaskService retryTaskService) {
        this.retryTaskService = retryTaskService;
    }

    //@Scheduled(fixedDelayString = "#{${interfacehub.scheduler.retry-fixed-delay-seconds:60} * 1000}")
    public void executeApprovedRetries() {
        List<RetryTask> approved = retryTaskService.findByStatus(RetryStatus.APPROVED);
        if (approved.isEmpty()) {
            return;
        }
        log.info("[RetryScheduler] Found {} APPROVED retry task(s) — executing", approved.size());

        for (RetryTask task : approved) {
            try {
                retryTaskService.executeApprovedRetry(task.getId());
                log.info("[RetryScheduler] Retry task {} executed successfully", task.getId());
            } catch (BusinessException e) {
                log.warn("[RetryScheduler] Retry task {} failed: {}", task.getId(), e.getMessage());
            } catch (Exception e) {
                log.error("[RetryScheduler] Retry task {} threw unexpected error", task.getId(), e);
            }
        }
    }
}
