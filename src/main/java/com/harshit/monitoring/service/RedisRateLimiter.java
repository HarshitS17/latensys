package com.harshit.monitoring.service;

import java.time.Clock;
import java.util.List;
import java.util.UUID;
import org.springframework.core.io.ClassPathResource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Service;

@Service
public class RedisRateLimiter {
    public record Decision(boolean allowed, long remaining, long retryAfterMillis) {}

    private final StringRedisTemplate redis;
    private final Clock testClock;
    private final DefaultRedisScript<List> script;

    @Autowired
    public RedisRateLimiter(StringRedisTemplate redis) {
        this(redis, null);
    }

    /** Deterministic time override used by sliding-window regression tests only. */
    public RedisRateLimiter(StringRedisTemplate redis, Clock testClock) {
        this.redis = redis;
        this.testClock = testClock;
        script = new DefaultRedisScript<>();
        script.setLocation(new ClassPathResource("rate-limit.lua"));
        script.setResultType(List.class);
    }

    public Decision check(String client, String uri, int maxRequests, int windowSeconds) {
        if (maxRequests < 1 || windowSeconds < 1) {
            throw new IllegalArgumentException("Rate limit and window must be positive");
        }
        String key = "rate:v2:" + client + ":" + uri;
        List<?> values = redis.execute(script, List.of(key),
                String.valueOf(windowSeconds * 1000L), String.valueOf(maxRequests),
                UUID.randomUUID().toString(), testClock == null ? "" : String.valueOf(testClock.millis()));
        if (values == null || values.size() != 3) {
            throw new IllegalStateException("Redis rate-limit script returned no decision");
        }
        return new Decision(((Number) values.get(0)).longValue() == 1,
                ((Number) values.get(1)).longValue(), ((Number) values.get(2)).longValue());
    }
}
