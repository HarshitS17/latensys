package com.harshit.monitoring.service;

import com.harshit.monitoring.repository.ApiRequestLogRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import java.time.Clock;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class MonitoringServiceTest {

    private ApiRequestLogRepository repository;
    private AlertService alerts;
    private MonitoringService service;

    @BeforeEach
    void setUp() {
        repository = mock(ApiRequestLogRepository.class);
        alerts = mock(AlertService.class);
        service = new MonitoringService(repository, mock(LatencySampleStore.class), alerts,
                Clock.systemDefaultZone());
    }

    @Test
    void highErrorRateTriggersAlert() {
        when(repository.countAllowedRequests()).thenReturn(100L);
        when(repository.countClientErrors()).thenReturn(7L);
        when(repository.countServerErrors()).thenReturn(4L);

        assertEquals(11.0, service.getErrorRate());
        verify(alerts).alertHighErrorRate(11.0);
    }

    @Test
    void emptyTrafficHasZeroErrorRateAndNoAlert() {
        when(repository.countAllowedRequests()).thenReturn(0L);

        assertEquals(0.0, service.getErrorRate());
        verify(alerts, never()).alertHighErrorRate(org.mockito.ArgumentMatchers.anyDouble());
    }

    @Test
    void accountingUsesMatchingLifetimeCounts() {
        when(repository.countAttempts()).thenReturn(100L);
        when(repository.countAllowedRequests()).thenReturn(80L);
        when(repository.countBlockedRequests()).thenReturn(15L);
        when(repository.countUnavailableRequests()).thenReturn(5L);
        when(repository.countSuccessfulRequests()).thenReturn(70L);
        when(repository.countClientErrors()).thenReturn(6L);
        when(repository.countServerErrors()).thenReturn(4L);

        MonitoringService.Accounting counts = service.getAccounting();
        assertEquals(15.0, counts.blockedPercentage());
        assertEquals(12.5, counts.errorRate());
        assertEquals(100L, counts.attempted());
        assertEquals(15L, counts.blocked());
    }
}
