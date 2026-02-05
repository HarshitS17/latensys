package com.harshit.monitoring.controller;

import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
public class RateLimitController {

    private final StringRedisTemplate redis;

    // Redis key used by rate-limiting filter
    private static final String BLOCKED_KEY = "rate:limit:blocked";

    // Rate limit configuration (should match filter)
    private static final int LIMIT = 30;
    private static final int WINDOW_SECONDS = 60;

    // constructor dependency injection
    public RateLimitController(StringRedisTemplate redis) {
        this.redis = redis;
    }

    /*
     Exposes rate limiting metrics for dashboard
    */
    @GetMapping("/monitor/rate-limit")
    public Map<String, Object> getRateLimitStats() {

        String blocked = redis.opsForValue().get(BLOCKED_KEY);
        long blockedCount = blocked == null ? 0 : Long.parseLong(blocked);

        return Map.of(
                "enabled", true,
                "limit", LIMIT,
                "windowSeconds", WINDOW_SECONDS,
                "blockedRequests", blockedCount
        );
    }
}