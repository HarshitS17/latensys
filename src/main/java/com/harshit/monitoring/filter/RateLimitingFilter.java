package com.harshit.monitoring.filter;

import jakarta.servlet.FilterChain; // used for  passing request to next filterChain
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest; // incoming req.  k liye
import jakarta.servlet.http.HttpServletResponse; // outgoing request k liye

import org.springframework.data.redis.core.StringRedisTemplate;// Redis template for interacting with Redis using String keys/values
import org.springframework.stereotype.Component; // to mark this as a spring manages component
import org.springframework.web.filter.OncePerRequestFilter; //ensures filter runs only once per request

import java.io.IOException;
import java.time.Duration; //Used to set TTL
// map to store endpoint wise rate limit rules
import java.util.HashMap;
import java.util.Map;

@Component
public class RateLimitingFilter extends OncePerRequestFilter {
    //redis client used to store and update rate-limit counters
    private final StringRedisTemplate redis;

    // Constructor injection
    public RateLimitingFilter(StringRedisTemplate redis) {
        this.redis = redis;
    }

    /* ------------ RATE LIMIT CONFIG ------------ */

    // Rule definition
    private static class RateLimitRule {
        int maxRequests;
        int windowSeconds;

        RateLimitRule(int maxRequests, int windowSeconds) {
            this.maxRequests = maxRequests;
            this.windowSeconds = windowSeconds;
        }
    }

    // Endpoint-specific rules
    private static final Map<String, RateLimitRule> RULES = new HashMap<>();

    static {
        RULES.put("/test/hello", new RateLimitRule(10, 60));
        RULES.put("/redis/test", new RateLimitRule(3, 60));
    }

    // Default rule
    private static final RateLimitRule DEFAULT_RULE =
            new RateLimitRule(50, 60);

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain
    ) throws ServletException, IOException {

        // skip monitoring and actuator endpoints
        String uri = request.getRequestURI();
        if (uri.startsWith("/monitor/") || uri.startsWith("/actuator")) {
            filterChain.doFilter(request, response);
            return;
        }

        // identify client by IP
        String ip = request.getRemoteAddr();

        // Get rule for endpoint
        RateLimitRule rule = RULES.getOrDefault(uri, DEFAULT_RULE);

        //redis key format
        String redisKey = "rate:" + ip + ":" + uri;

        // incrementing request count automatically
        Long count = redis.opsForValue().increment(redisKey);

        //if it is the first request : set TTL => Time TO Live
        if (count != null && count == 1) {
            redis.expire(redisKey, Duration.ofSeconds(rule.windowSeconds));
        }

        // block if the limit is crossed
        if (count != null && count > rule.maxRequests) {

            // increment global blocked request counter
            redis.opsForValue().increment("rate:limit:blocked");

            response.setStatus(429); // too many requests
            response.getWriter().println("Too many requests!");
            return;
        }



        // allow request
        filterChain.doFilter(request, response);
    }

    //Run this filter EVEN for error / 429 responses
    @Override
    protected boolean shouldNotFilterErrorDispatch() {
        return false;
    }
}
