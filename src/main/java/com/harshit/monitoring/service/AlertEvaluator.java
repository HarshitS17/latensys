package com.harshit.monitoring.service;

import com.harshit.monitoring.dto.EndpointStatsDTO;
import java.time.Clock;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
public class AlertEvaluator {
    private static final Logger log = LoggerFactory.getLogger(AlertEvaluator.class);
    private final MonitoringService monitoring;
    private final AlertService alerts;
    private final Clock clock;
    private final int minRequests;
    private final AtomicBoolean running = new AtomicBoolean();

    public AlertEvaluator(MonitoringService monitoring, AlertService alerts, Clock clock,
                          @Value("${alerts.min-requests:20}") int minRequests) {
        if (minRequests < 1) throw new IllegalArgumentException("Minimum alert sample count must be positive");
        this.monitoring = monitoring;
        this.alerts = alerts;
        this.clock = clock;
        this.minRequests = minRequests;
    }

    public void evaluate() {
        if (!running.compareAndSet(false, true)) return;
        try {
            LocalDateTime now = LocalDateTime.now(clock);
            LocalDateTime minuteEnd = now.truncatedTo(ChronoUnit.MINUTES);
            // Gather all data before notifying; unavailable metrics must not produce partial alerts.
            MonitoringService.Accounting lastFiveMinutes =
                    monitoring.getAccountingBetween(now.minusMinutes(5), now);
            MonitoringService.Accounting lastCompleteMinute =
                    monitoring.getAccountingBetween(minuteEnd.minusMinutes(1), minuteEnd);
            List<EndpointStatsDTO> endpoints = monitoring.getEndpointStats(5);

            if (lastFiveMinutes.allowed() >= minRequests && lastFiveMinutes.errorRate() > 10) {
                alerts.alertHighErrorRate(lastFiveMinutes.errorRate());
            }
            if (lastCompleteMinute.attempted() > 1000) {
                alerts.alertHighTraffic(lastCompleteMinute.attempted());
            }
            if (lastCompleteMinute.blocked() > 50) {
                alerts.alertRateLimitingActive(lastCompleteMinute.blocked());
            }
            for (EndpointStatsDTO endpoint : endpoints) {
                if (endpoint.getTotalRequests() >= minRequests
                        && endpoint.getP95Latency() != null && endpoint.getP95Latency() > 1000) {
                    alerts.alertSlowEndpoint(endpoint.getUri(), endpoint.getP95Latency());
                }
            }
        } catch (RuntimeException e) {
            log.warn("Alert evaluation unavailable ({})", e.getClass().getSimpleName());
        } finally {
            running.set(false);
        }
    }
}
