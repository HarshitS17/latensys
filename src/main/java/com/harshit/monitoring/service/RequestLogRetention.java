package com.harshit.monitoring.service;

import com.harshit.monitoring.repository.ApiRequestLogRepository;
import java.time.Clock;
import java.time.LocalDateTime;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class RequestLogRetention {
    private final ApiRequestLogRepository repository;
    private final Clock clock;
    private final int days;
    private final int batchSize;

    public RequestLogRetention(ApiRequestLogRepository repository, Clock clock,
                               @Value("${retention.days:30}") int days,
                               @Value("${retention.batch-size:5000}") int batchSize) {
        if (days < 1 || batchSize < 1) throw new IllegalArgumentException("Retention settings must be positive");
        this.repository = repository;
        this.clock = clock;
        this.days = days;
        this.batchSize = batchSize;
    }

    @Transactional
    public int deleteOneBatch() {
        return repository.deleteExpiredBatch(LocalDateTime.now(clock).minusDays(days), batchSize);
    }

    public int batchSize() { return batchSize; }
}
