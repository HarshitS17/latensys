package com.harshit.monitoring.filter;

import com.harshit.monitoring.entity.ApiRequestLog;
import com.harshit.monitoring.config.AppProxyConfig;
import com.harshit.monitoring.repository.ApiRequestLogRepository;
import com.harshit.monitoring.service.LatencySampleStore;
import jakarta.servlet.FilterChain;
import jakarta.servlet.DispatcherType;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.concurrent.TimeUnit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.filter.OncePerRequestFilter;

@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class MonitoringFilter extends OncePerRequestFilter {
    private static final Logger log = LoggerFactory.getLogger(MonitoringFilter.class);
    private final ApiRequestLogRepository repository;
    private final LatencySampleStore samples;
    private final AppProxyConfig appProxy;

    public MonitoringFilter(ApiRequestLogRepository repository, LatencySampleStore samples) {
        this(repository, samples, null);
    }

    @Autowired
    public MonitoringFilter(ApiRequestLogRepository repository, LatencySampleStore samples, AppProxyConfig appProxy) {
        this.repository = repository;
        this.samples = samples;
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
        long start = System.nanoTime();
        boolean completed = false;
        try {
            chain.doFilter(request, response);
            completed = true;
        } finally {
            long latency = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - start);
            int status = !completed && response.getStatus() < 400 ? 500 : response.getStatus();
            String decision = (String) request.getAttribute(RateLimitingFilter.DECISION_ATTRIBUTE);
            if (decision == null) decision = "ALLOWED";
            try {
                repository.save(new ApiRequestLog(request.getMethod(), metricPath(request.getRequestURI()),
                        status, latency, decision));
            } catch (RuntimeException e) {
                log.warn("PostgreSQL monitoring write failed ({})", e.getClass().getSimpleName());
            }
            if ("ALLOWED".equals(decision)) {
                try {
                    samples.record(metricPath(request.getRequestURI()), latency);
                } catch (RuntimeException e) {
                    log.warn("Redis latency write failed ({})", e.getClass().getSimpleName());
                }
            }
        }
    }

    private String metricPath(String uri) {
        return RequestPaths.metricPath(uri, appProxy == null ? java.util.List.of() : appProxy.getAllowedPaths());
    }
}
