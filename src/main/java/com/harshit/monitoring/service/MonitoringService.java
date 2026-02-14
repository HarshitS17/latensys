package com.harshit.monitoring.service;

import com.harshit.monitoring.dto.EndpointStatsDTO;
import com.harshit.monitoring.repository.ApiRequestLogRepository;

import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/*
 MonitoringService
 -----------------
 Centralized business logic for:
 - Global metrics
 - Error rate
 - Window-based endpoint metrics
 - Redis-based P95 latency calculation
*/
@Service
public class MonitoringService {

    private final ApiRequestLogRepository repository;
    private final StringRedisTemplate redis;

    public MonitoringService(ApiRequestLogRepository repository,
                             StringRedisTemplate redis) {
        this.repository = repository;
        this.redis = redis;
    }

    /* ================= GLOBAL METRICS ================= */

    public long getTotalRequests() {
        return repository.countNonMonitorRequests();
    }

    public double getAverageResponseTime() {
        Double avg = repository.getGlobalAverageResponseTime();
        return avg == null ? 0 : avg;
    }

    public double getErrorRate() {

        long total = repository.countNonMonitorRequests();
        if (total == 0) return 0;

        long errors = repository.countErrorRequests();

        return (errors * 100.0) / total;
    }

    /* ======================================================
       WINDOW-BASED ENDPOINT STATS
       This method now respects selected 5min / 15min window
       ====================================================== */
    public List<EndpointStatsDTO> getEndpointStats(int windowMinutes) {

        List<EndpointStatsDTO> result = new ArrayList<>();

        // Compute cutoff timestamp
        LocalDateTime cutoff =
                LocalDateTime.now().minusMinutes(windowMinutes);

        // Fetch only URIs active in that window
        Set<String> uris =
                repository.findDistinctUrisAfter(cutoff);

        for (String uri : uris) {

            // Count requests within window
            long count =
                    repository.countByUriAfter(uri, cutoff);

            // Avg latency within window
            Double avg =
                    repository.getAverageResponseTimeByUriAfter(uri, cutoff);

            double avgLatency = avg == null ? 0 : avg;

            // Redis-based P95 within same window
            double p95 = getP95Latency(uri, windowMinutes);

            result.add(new EndpointStatsDTO(
                    uri,
                    count,
                    avgLatency,
                    p95
            ));
        }

        return result;
    }

    /* ======================================================
       REDIS-BASED P95 CALCULATION
       Sorted Set:
         score = timestamp
         value = latency
       ====================================================== */
    public double getP95Latency(String uri, int windowMinutes) {

        String key = "latency:" + uri + ":" + windowMinutes + "m";

        Long size = redis.opsForZSet().size(key);
        if (size == null || size == 0) return 0;

        // 95th percentile index
        long index = (long) Math.ceil(0.95 * size) - 1;

        // Get latency value at percentile index
        Set<String> values =
                redis.opsForZSet().range(key, index, index);

        if (values == null || values.isEmpty()) return 0;

        String latencyValue = values.iterator().next();

        return Double.parseDouble(latencyValue);
    }
}