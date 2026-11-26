package com.harshit.monitoring.service;

import com.harshit.monitoring.repository.ApiRequestLogRepository;
import java.time.Clock;
import java.util.Collections;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.mockito.ArgumentMatchers.any;

class MonitoringServiceTest {
    private ApiRequestLogRepository repository;
    private MonitoringService service;

    @BeforeEach
    void setUp() {
        repository = mock(ApiRequestLogRepository.class);
        service = new MonitoringService(repository, mock(LatencySampleStore.class), Clock.systemDefaultZone(), 30);
    }

    @Test
    void groupedAccountingUsesMatchingRetainedCounts() {
        when(repository.aggregateRetained(any())).thenReturn(Collections.singletonList(new Object[]{
                100L, 80L, 15L, 5L, 70L, 6L, 4L, 25.0}));
        MonitoringService.Accounting counts = service.getAccounting();
        assertEquals(15.0, counts.blockedPercentage());
        assertEquals(12.5, counts.errorRate());
        assertEquals(25.0, counts.averageResponseTime());
        assertEquals(100, counts.attempted());
    }

    @Test
    void emptyDataHasNoFabricatedErrors() {
        when(repository.aggregateRetained(any())).thenReturn(Collections.singletonList(new Object[]{
                0L, 0L, 0L, 0L, 0L, 0L, 0L, null}));
        assertEquals(0.0, service.getErrorRate());
        assertEquals(0.0, service.getAccounting().blockedPercentage());
    }
}
