package com.harshit.monitoring.service;

import com.harshit.monitoring.dto.EndpointStatsDTO;
import com.harshit.monitoring.repository.ApiRequestLogRepository;
import java.time.Clock;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import org.springframework.stereotype.Service;

@Service
public class MonitoringService {
    public record Accounting(long attempted, long allowed, long blocked, long unavailable,
                             long successful, long clientErrors, long serverErrors,
                             double errorRate, double blockedPercentage) {}

    private final ApiRequestLogRepository repository;
    private final LatencySampleStore samples;
    private final AlertService alertService;
    private final Clock clock;

    public MonitoringService(ApiRequestLogRepository repository, LatencySampleStore samples,
                             AlertService alertService, Clock clock) {
        this.repository = repository;
        this.samples = samples;
        this.alertService = alertService;
        this.clock = clock;
    }

    public Accounting getAccounting() {
        long attempted = repository.countAttempts();
        long allowed = repository.countAllowedRequests();
        long blocked = repository.countBlockedRequests();
        long unavailable = repository.countUnavailableRequests();
        long successful = repository.countSuccessfulRequests();
        long clientErrors = repository.countClientErrors();
        long serverErrors = repository.countServerErrors();
        double errorRate = allowed == 0 ? 0 : 100.0 * (clientErrors + serverErrors) / allowed;
        double blockedPercentage = attempted == 0 ? 0 : 100.0 * blocked / attempted;
        if (errorRate > 10) alertService.alertHighErrorRate(errorRate);
        return new Accounting(attempted, allowed, blocked, unavailable, successful,
                clientErrors, serverErrors, errorRate, blockedPercentage);
    }

    public long getTotalRequests() {
        return repository.countAttempts();
    }

    public double getAverageResponseTime() {
        Double average = repository.getGlobalAverageResponseTime();
        return average == null ? 0 : average;
    }

    public double getErrorRate() {
        return getAccounting().errorRate();
    }

    public List<EndpointStatsDTO> getEndpointStats(int windowMinutes) {
        validateWindow(windowMinutes);
        LocalDateTime cutoff = LocalDateTime.now(clock).minusMinutes(windowMinutes);
        Set<String> uris = repository.findDistinctUrisAfter(cutoff);
        List<EndpointStatsDTO> result = new ArrayList<>();
        for (String uri : uris) {
            long count = repository.countByUriAfter(uri, cutoff);
            Double average = repository.getAverageResponseTimeByUriAfter(uri, cutoff);
            Double p95 = getP95Latency(uri, windowMinutes);
            if (p95 != null && p95 > 1000) alertService.alertSlowEndpoint(uri, p95);
            result.add(new EndpointStatsDTO(uri, count, average == null ? 0 : average, p95));
        }
        return result;
    }

    public Double getP95Latency(String uri, int windowMinutes) {
        validateWindow(windowMinutes);
        return samples.p95(uri, windowMinutes);
    }

    public List<Long> getLast15MinuteRPM() {
        return minuteCounts(false);
    }

    public List<Long> getLast15MinuteBlocked() {
        return minuteCounts(true);
    }

    private List<Long> minuteCounts(boolean blocked) {
        LocalDateTime currentMinute = LocalDateTime.now(clock).truncatedTo(ChronoUnit.MINUTES);
        List<Long> result = new ArrayList<>(15);
        for (int i = 14; i >= 0; i--) {
            LocalDateTime start = currentMinute.minusMinutes(i);
            long count = blocked ? repository.countBlockedBetween(start, start.plusMinutes(1))
                    : repository.countAttemptsBetween(start, start.plusMinutes(1));
            result.add(count);
            if (blocked && count > 50) alertService.alertRateLimitingActive(count);
            if (!blocked && count > 1000) alertService.alertHighTraffic(count);
        }
        return result;
    }

    private void validateWindow(int windowMinutes) {
        if (windowMinutes != 5 && windowMinutes != 15) {
            throw new IllegalArgumentException("Supported windows are 5 and 15 minutes");
        }
    }
}
