package com.harshit.monitoring.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(prefix = "retention", name = "enabled", havingValue = "true", matchIfMissing = true)
public class RequestLogRetentionScheduler {
    private static final Logger log = LoggerFactory.getLogger(RequestLogRetentionScheduler.class);
    private final RequestLogRetention retention;
    private final int maxBatches;

    public RequestLogRetentionScheduler(RequestLogRetention retention,
                                        @Value("${retention.max-batches:10}") int maxBatches) {
        if (maxBatches < 1) throw new IllegalArgumentException("Maximum batches must be positive");
        this.retention = retention;
        this.maxBatches = maxBatches;
    }

    @Scheduled(fixedDelayString = "${retention.cleanup-delay-ms:3600000}", initialDelayString = "${retention.cleanup-delay-ms:3600000}")
    public void runCleanup() {
        try {
            int deleted = 0;
            for (int i = 0; i < maxBatches; i++) {
                int batch = retention.deleteOneBatch();
                deleted += batch;
                if (batch < retention.batchSize()) break;
            }
            if (deleted > 0) log.info("Deleted {} expired request logs", deleted);
        } catch (RuntimeException e) {
            log.warn("Request-log cleanup failed ({})", e.getClass().getSimpleName());
        }
    }
}
