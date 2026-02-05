package com.harshit.monitoring.filter;

import com.harshit.monitoring.entity.ApiRequestLog;
import com.harshit.monitoring.repository.ApiRequestLogRepository;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/*
 This filter runs for EVERY incoming HTTP request.
 It logs request metadata + pushes latency samples to Redis
*/
@Component
public class MonitoringFilter extends OncePerRequestFilter {

    private final ApiRequestLogRepository repository;
    private final StringRedisTemplate redis;

    public MonitoringFilter(ApiRequestLogRepository repository,
                            StringRedisTemplate redis) {
        this.repository = repository;
        this.redis = redis;
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain
    ) throws ServletException, IOException {

        String uri = request.getRequestURI();

        /*
         Ignore dashboard + static files
        */
        if (
                uri.equals("/") ||
                        uri.startsWith("/monitor") ||
                        uri.startsWith("/actuator") ||
                        uri.equals("/error") ||
                        uri.endsWith(".css") ||
                        uri.endsWith(".js") ||
                        uri.endsWith(".html") ||
                        uri.startsWith("/favicon")
        ) {
            filterChain.doFilter(request, response);
            return;
        }

        long start = System.currentTimeMillis();

        filterChain.doFilter(request, response);

        long end = System.currentTimeMillis();
        long latency = end - start;

        // ---------- DB LOG ----------
        repository.save(new ApiRequestLog(
                request.getMethod(),
                uri,
                response.getStatus(),
                latency
        ));

        // ---------- REDIS (P95 windows) ----------
        long now = System.currentTimeMillis();

        redis.opsForZSet().add("latency:" + uri + ":5m",  String.valueOf(latency), now);
        redis.opsForZSet().add("latency:" + uri + ":15m", String.valueOf(latency), now);

        // cleanup old samples
        redis.opsForZSet().removeRangeByScore(
                "latency:" + uri + ":5m",
                0, now - (5 * 60 * 1000)
        );

        redis.opsForZSet().removeRangeByScore(
                "latency:" + uri + ":15m",
                0, now - (15 * 60 * 1000)
        );
    }

    @Override
    protected boolean shouldNotFilterErrorDispatch() {
        return false;
    }
}