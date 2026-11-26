package com.harshit.monitoring.filter;

import com.harshit.monitoring.config.RateLimitConfig;
import com.harshit.monitoring.config.AppProxyConfig;
import com.harshit.monitoring.service.RedisRateLimiter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.DispatcherType;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.filter.OncePerRequestFilter;

@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 1)
public class RateLimitingFilter extends OncePerRequestFilter {
    private static final Logger log = LoggerFactory.getLogger(RateLimitingFilter.class);
    public static final String DECISION_ATTRIBUTE = RateLimitingFilter.class.getName() + ".decision";

    private final RedisRateLimiter limiter;
    private final RateLimitConfig config;
    private final AppProxyConfig appProxy;

    public RateLimitingFilter(RedisRateLimiter limiter, RateLimitConfig config) {
        this(limiter, config, null);
    }

    @Autowired
    public RateLimitingFilter(RedisRateLimiter limiter, RateLimitConfig config, AppProxyConfig appProxy) {
        this.limiter = limiter;
        this.config = config;
        this.appProxy = appProxy;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return request.getDispatcherType() == DispatcherType.ERROR
                || RequestPaths.isInternal(request.getRequestURI());
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain chain) throws ServletException, IOException {
        String uri = RequestPaths.metricPath(request.getRequestURI(),
                appProxy == null ? java.util.List.of() : appProxy.getAllowedPaths());
        RateLimitConfig.Policy rule = config.resolve(uri);
        RedisRateLimiter.Decision decision;
        try {
            decision = limiter.check(request.getRemoteAddr(), uri,
                    rule.getMaxRequests(), rule.getWindowSeconds());
        } catch (RuntimeException e) {
            log.warn("Rate-limit decision unavailable ({})", e.getClass().getSimpleName());
            request.setAttribute(DECISION_ATTRIBUTE, "UNAVAILABLE");
            response.setStatus(HttpServletResponse.SC_SERVICE_UNAVAILABLE);
            response.setContentType("application/json");
            response.getWriter().write("{\"error\":\"rate_limit_unavailable\"}");
            return;
        }

        response.setHeader("X-RateLimit-Remaining", String.valueOf(decision.remaining()));
        if (!decision.allowed()) {
            request.setAttribute(DECISION_ATTRIBUTE, "BLOCKED");
            long retrySeconds = Math.max(1, (decision.retryAfterMillis() + 999) / 1000);
            response.setHeader("Retry-After", String.valueOf(retrySeconds));
            response.setStatus(429);
            response.setContentType("application/json");
            response.getWriter().write("{\"error\":\"rate_limited\",\"retryAfterSeconds\":"
                    + retrySeconds + "}");
            return;
        }
        request.setAttribute(DECISION_ATTRIBUTE, "ALLOWED");
        chain.doFilter(request, response);
    }
}
