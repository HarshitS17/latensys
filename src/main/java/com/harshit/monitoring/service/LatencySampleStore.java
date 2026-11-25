package com.harshit.monitoring.service;

import java.time.Clock;
import java.time.Duration;
import java.util.Arrays;
import java.util.Set;
import java.util.UUID;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

@Service
public class LatencySampleStore {
    private static final long RETENTION_MILLIS = Duration.ofMinutes(15).toMillis();
    private static final Duration KEY_TTL = Duration.ofMinutes(16);
    private final StringRedisTemplate redis;
    private final Clock clock;

    public LatencySampleStore(StringRedisTemplate redis, Clock clock) {
        this.redis = redis;
        this.clock = clock;
    }

    public void record(String uri, long latencyMillis) {
        long now = clock.millis();
        String key = key(uri);
        redis.opsForZSet().removeRangeByScore(key, Double.NEGATIVE_INFINITY, now - RETENTION_MILLIS);
        redis.opsForZSet().add(key, latencyMillis + ":" + UUID.randomUUID(), now);
        redis.expire(key, KEY_TTL);
    }

    /** Exact nearest-rank percentile. Reading n samples costs O(n log n) time and O(n) memory. */
    public Double p95(String uri, int windowMinutes) {
        if (windowMinutes != 5 && windowMinutes != 15) {
            throw new IllegalArgumentException("Supported windows are 5 and 15 minutes");
        }
        long now = clock.millis();
        // Exclusive lower bound: a sample exactly at the cutoff has expired.
        Set<String> members = redis.opsForZSet().rangeByScore(
                key(uri), now - Duration.ofMinutes(windowMinutes).toMillis() + 1, now);
        if (members == null || members.isEmpty()) return null;
        long[] latencies = members.stream().mapToLong(member ->
                Long.parseLong(member.substring(0, member.indexOf(':')))).toArray();
        Arrays.sort(latencies);
        int index = (int) Math.ceil(0.95 * latencies.length) - 1;
        return (double) latencies[index];
    }

    private String key(String uri) {
        return "latency:v2:" + uri;
    }
}
