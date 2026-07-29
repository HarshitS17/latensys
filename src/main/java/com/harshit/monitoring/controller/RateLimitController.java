package com.harshit.monitoring.controller;

import com.harshit.monitoring.config.RateLimitConfig;

import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
public class RateLimitController {

    private final StringRedisTemplate redis;

    // ✅ FIX: was hardcoded to LIMIT=30 / WINDOW=60 which disagreed with
    //   RateLimitingFilter's DEFAULT_RULE (50/60). Now both read the same bean.
    private final RateLimitConfig rateLimitConfig;

    // Redis key used by rate-limiting filter
    private static final String BLOCKED_KEY = "rate:limit:blocked";

    // constructor dependency injection
    public RateLimitController(StringRedisTemplate redis, RateLimitConfig rateLimitConfig) {
        this.redis = redis;
        this.rateLimitConfig = rateLimitConfig;
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
                "limit", rateLimitConfig.getDefaultMaxRequests(),
                "windowSeconds", rateLimitConfig.getDefaultWindowSeconds(),
                "blockedRequests", blockedCount
        );
    }
}