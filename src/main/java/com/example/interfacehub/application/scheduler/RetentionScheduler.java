package com.example.interfacehub.application.scheduler;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(name = "interfacehub.scheduler.enabled", havingValue = "true", matchIfMissing = true)
public class RetentionScheduler {

    private static final Logger log = LoggerFactory.getLogger(RetentionScheduler.class);

    private final RetentionService retentionService;

    public RetentionScheduler(RetentionService retentionService) {
        this.retentionService = retentionService;
    }

    // runs every night at 02:00
    @Scheduled(cron = "0 0 2 * * *")
    public void runNightlyRetention() {
        log.info("[RetentionScheduler] Starting nightly archive and purge");
        try {
            RetentionService.RetentionResult result = retentionService.archiveAndPurge();
            log.info("[RetentionScheduler] Completed — {}", result);
        } catch (Exception e) {
            log.error("[RetentionScheduler] Nightly retention failed", e);
        }
    }
}
