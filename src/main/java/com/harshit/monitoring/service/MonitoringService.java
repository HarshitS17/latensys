package com.harshit.monitoring.service;

import com.harshit.monitoring.dto.EndpointStatsDTO;
import com.harshit.monitoring.repository.ApiRequestLogRepository;

import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/*
 This service contains ALL monitoring calculations.
 Filters only collect data, controllers only expose data.
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

    /* Total API requests */
    public long getTotalRequests() {
        return repository.countNonMonitorRequests();
    }

    /* Global average latency */
    public double getAverageResponseTime() {
        Double avg = repository.getGlobalAverageResponseTime();
        return avg == null ? 0 : avg;
    }

    /* Error rate (%) */
    public double getErrorRate() {
        long total = repository.countNonMonitorRequests();
        if (total == 0) return 0;

        long errors = repository.countErrorRequests();
        return (errors * 100.0) / total;
    }

    /*
     Endpoint metrics with time-windowed P95
    */
    public List<EndpointStatsDTO> getEndpointStats(int windowMinutes) {

        List<EndpointStatsDTO> result = new ArrayList<>();
        Set<String> uris = repository.findDistinctUris();

        for (String uri : uris) {

            long count = repository.countByUri(uri);

            Double avg = repository.getAverageResponseTimeByUri(uri);
            double avgLatency = avg == null ? 0 : avg;

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

    /*
     Redis-based P95 calculation
    */
    public double getP95Latency(String uri, int windowMinutes) {

        String key = "latency:" + uri + ":" + windowMinutes + "m";

        Long size = redis.opsForZSet().size(key);
        if (size == null || size == 0) return 0;

        long index = (long) Math.ceil(0.95 * size) - 1;

        var values = redis.opsForZSet().range(key, index, index);
        if (values == null || values.isEmpty()) return 0;

        Double score = redis.opsForZSet()
                .score(key, values.iterator().next());

        return score == null ? 0 : score;
    }
}