package com.harshit.monitoring.service;

import com.harshit.monitoring.dto.EndpointStatsDTO;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import org.junit.jupiter.api.Test;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class AlertEvaluatorTest {
    private final MonitoringService monitoring = mock(MonitoringService.class);
    private final AlertService alerts = mock(AlertService.class);
    private final Clock clock = Clock.fixed(Instant.parse("2026-11-26T12:00:00Z"), ZoneOffset.UTC);
    private final AlertEvaluator evaluator = new AlertEvaluator(monitoring, alerts, clock, 20);

    @Test
    void evaluatesPreservedThresholdsWithEnoughData() {
        when(monitoring.getAccountingBetween(any(), any())).thenReturn(
                accounting(100, 100, 0, 11.0), accounting(1052, 1001, 51, 0));
        when(monitoring.getEndpointStats(5)).thenReturn(List.of(
                new EndpointStatsDTO("/slow", 20, 100, 1100.0)));
        evaluator.evaluate();
        verify(alerts).alertHighErrorRate(11.0);
        verify(alerts).alertHighTraffic(1052);
        verify(alerts).alertRateLimitingActive(51);
        verify(alerts).alertSlowEndpoint("/slow", 1100.0);
    }

    @Test
    void skipsInsufficientData() {
        when(monitoring.getAccountingBetween(any(), any())).thenReturn(
                accounting(19, 19, 0, 50.0), accounting(1, 1, 0, 0));
        when(monitoring.getEndpointStats(5)).thenReturn(List.of(
                new EndpointStatsDTO("/slow", 19, 100, 1100.0)));
        evaluator.evaluate();
        verifyNoInteractions(alerts);
    }

    @Test
    void unavailableMetricsDoNotSendAlerts() {
        when(monitoring.getAccountingBetween(any(), any())).thenThrow(new IllegalStateException("unavailable"));
        evaluator.evaluate();
        verifyNoInteractions(alerts);
    }

    private MonitoringService.Accounting accounting(long attempted, long allowed, long blocked,
                                                     double errorRate) {
        return new MonitoringService.Accounting(attempted, allowed, blocked, 0, allowed,
                0, 0, 0, errorRate, attempted == 0 ? 0 : 100.0 * blocked / attempted);
    }
}
