package com.harshit.monitoring.service;

import com.harshit.monitoring.dto.EndpointStatsDTO;
import com.harshit.monitoring.repository.ApiRequestLogRepository;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Service;
import org.springframework.beans.factory.annotation.Value;

@Service
public class MonitoringService {
    public record Accounting(long attempted, long allowed, long blocked, long unavailable,
                             long successful, long clientErrors, long serverErrors,
                             double averageResponseTime, double errorRate,
                             double blockedPercentage) {}
    public record MinuteTrends(List<Long> attempted, List<Long> blocked) {}

    private final ApiRequestLogRepository repository;
    private final LatencySampleStore samples;
    private final Clock clock;
    private final int retentionDays;

    public MonitoringService(ApiRequestLogRepository repository, LatencySampleStore samples, Clock clock,
                             @Value("${retention.days:30}") int retentionDays) {
        if (retentionDays < 1) throw new IllegalArgumentException("Retention days must be positive");
        this.repository = repository;
        this.samples = samples;
        this.clock = clock;
        this.retentionDays = retentionDays;
    }

    public Accounting getAccounting() {
        return accounting(repository.aggregateRetained(LocalDateTime.now(clock).minusDays(retentionDays)).get(0));
    }

    public Accounting getAccountingBetween(LocalDateTime start, LocalDateTime end) {
        return accounting(repository.aggregateBetween(start, end).get(0));
    }

    public long getTotalRequests() { return getAccounting().attempted(); }
    public double getAverageResponseTime() { return getAccounting().averageResponseTime(); }
    public double getErrorRate() { return getAccounting().errorRate(); }

    private Accounting accounting(Object[] row) {
        long attempted = number(row[0]);
        long allowed = number(row[1]);
        long blocked = number(row[2]);
        long unavailable = number(row[3]);
        long successful = number(row[4]);
        long clientErrors = number(row[5]);
        long serverErrors = number(row[6]);
        double average = row[7] == null ? 0 : ((Number) row[7]).doubleValue();
        double errorRate = allowed == 0 ? 0 : 100.0 * (clientErrors + serverErrors) / allowed;
        double blockedPercentage = attempted == 0 ? 0 : 100.0 * blocked / attempted;
        return new Accounting(attempted, allowed, blocked, unavailable,
                successful, clientErrors, serverErrors, average, errorRate, blockedPercentage);
    }

    public List<EndpointStatsDTO> getEndpointStats(int windowMinutes) {
        validateWindow(windowMinutes);
        LocalDateTime cutoff = LocalDateTime.now(clock).minusMinutes(windowMinutes);
        List<EndpointStatsDTO> result = new ArrayList<>();
        for (Object[] row : repository.aggregateEndpointsAfter(cutoff)) {
            String uri = (String) row[0];
            result.add(new EndpointStatsDTO(uri, number(row[1]), ((Number) row[2]).doubleValue(),
                    samples.p95(uri, windowMinutes)));
        }
        return result;
    }

    public Double getP95Latency(String uri, int windowMinutes) {
        validateWindow(windowMinutes);
        return samples.p95(uri, windowMinutes);
    }

    public MinuteTrends getMinuteTrends() {
        LocalDateTime current = LocalDateTime.now(clock).truncatedTo(ChronoUnit.MINUTES);
        LocalDateTime start = current.minusMinutes(14);
        Map<LocalDateTime, long[]> buckets = new HashMap<>();
        for (Object[] row : repository.aggregateMinutes(start, current.plusMinutes(1))) {
            LocalDateTime minute = row[0] instanceof Timestamp timestamp
                    ? timestamp.toLocalDateTime() : (LocalDateTime) row[0];
            buckets.put(minute, new long[]{number(row[1]), number(row[2])});
        }
        List<Long> attempted = new ArrayList<>(15);
        List<Long> blocked = new ArrayList<>(15);
        for (int i = 14; i >= 0; i--) {
            long[] counts = buckets.getOrDefault(current.minusMinutes(i), new long[]{0, 0});
            attempted.add(counts[0]);
            blocked.add(counts[1]);
        }
        return new MinuteTrends(attempted, blocked);
    }

    public List<Long> getLast15MinuteRPM() { return getMinuteTrends().attempted(); }
    public List<Long> getLast15MinuteBlocked() { return getMinuteTrends().blocked(); }

    private long number(Object value) { return ((Number) value).longValue(); }

    private void validateWindow(int windowMinutes) {
        if (windowMinutes != 5 && windowMinutes != 15) {
            throw new IllegalArgumentException("Supported windows are 5 and 15 minutes");
        }
    }
}
