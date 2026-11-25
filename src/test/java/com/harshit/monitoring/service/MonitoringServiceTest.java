package com.harshit.monitoring.service;

import com.harshit.monitoring.repository.ApiRequestLogRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.StringRedisTemplate;

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
        service = new MonitoringService(repository, mock(StringRedisTemplate.class), alerts);
    }

    @Test
    void highErrorRateTriggersAlert() {
        when(repository.countNonMonitorRequests()).thenReturn(100L);
        when(repository.countErrorRequests()).thenReturn(11L);

        assertEquals(11.0, service.getErrorRate());
        verify(alerts).alertHighErrorRate(11.0);
    }

    @Test
    void emptyTrafficHasZeroErrorRateAndNoAlert() {
        when(repository.countNonMonitorRequests()).thenReturn(0L);

        assertEquals(0.0, service.getErrorRate());
        verify(alerts, never()).alertHighErrorRate(org.mockito.ArgumentMatchers.anyDouble());
    }
}
