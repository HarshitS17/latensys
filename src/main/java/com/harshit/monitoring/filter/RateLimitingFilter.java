package com.harshit.monitoring.filter;

import jakarta.servlet.FilterChain;              // Pass request to next filter/controller
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;  // Incoming HTTP request
import jakarta.servlet.http.HttpServletResponse; // Outgoing HTTP response

import org.springframework.data.redis.core.StringRedisTemplate; // Redis operations
import org.springframework.stereotype.Component;                // Spring-managed bean
import org.springframework.web.filter.OncePerRequestFilter;     // Run filter once per request

import java.io.IOException;
import java.time.Duration; // Used for TTL
import java.util.HashMap;
import java.util.Map;

@Component
public class RateLimitingFilter extends OncePerRequestFilter {

    // Redis client for rate limiting
    private final StringRedisTemplate redis;

    public RateLimitingFilter(StringRedisTemplate redis) {
        this.redis = redis;
    }

    /* ------------ RATE LIMIT CONFIG ------------ */

    // Defines rule: max requests + time window
    private static class RateLimitRule {
        int maxRequests;     // max allowed requests
        int windowSeconds;   // time window (seconds)

        RateLimitRule(int maxRequests, int windowSeconds) {
            this.maxRequests = maxRequests;
            this.windowSeconds = windowSeconds;
        }
    }

    // Custom rules per endpoint
    private static final Map<String, RateLimitRule> RULES = new HashMap<>();

    static {
        RULES.put("/test/hello", new RateLimitRule(10, 60));
        RULES.put("/redis/test", new RateLimitRule(3, 60));
    }

    // Default rule for other endpoints
    private static final RateLimitRule DEFAULT_RULE =
            new RateLimitRule(50, 60);

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain
    ) throws ServletException, IOException {

        // Get requested API path
        String uri = request.getRequestURI();

        // Skip monitoring & actuator endpoints
        if (uri.startsWith("/monitor") || uri.startsWith("/actuator")) {
            filterChain.doFilter(request, response);
            return;
        }

        // Identify client by IP
        String ip = request.getRemoteAddr();

        // Select rule for endpoint
        RateLimitRule rule = RULES.getOrDefault(uri, DEFAULT_RULE);

        // Redis key format: rate:<ip>:<uri>
        String redisKey = "rate:" + ip + ":" + uri;

        // Current time in milliseconds
        long now = System.currentTimeMillis();

        // Calculate start of sliding window
        long windowStart = now - (rule.windowSeconds * 1000L);

        // Remove old requests outside current window
        redis.opsForZSet().removeRangeByScore(redisKey, 0, windowStart);

        //  Count requests still inside window
        Long currentCount = redis.opsForZSet().zCard(redisKey);

        // If limit exceeded → block request
        if (currentCount != null && currentCount >= rule.maxRequests) {

            // Track total blocked requests
            redis.opsForValue().increment("rate:limit:blocked");

            response.setStatus(429); // Too Many Requests
            response.getWriter().println("Too many requests!");
            return;
        }

        //  Add current request timestamp to sorted set
        redis.opsForZSet().add(redisKey, String.valueOf(now), now);

        // Optional TTL (cleanup inactive keys)
        redis.expire(redisKey, Duration.ofSeconds(rule.windowSeconds));

        // Allow request to continue
        filterChain.doFilter(request, response);

        //------ blocked requests are tracked per minute.------
        long currentMinute = now / 60000;

        String blockedKey = "metrics:blocked:" + currentMinute;

        redis.opsForValue().increment(blockedKey);
        redis.expire(blockedKey, Duration.ofMinutes(20));

    }

    @Override
    protected boolean shouldNotFilterErrorDispatch() {
        return false; // Apply filter even on error dispatch
    }
}