package com.harshit.monitoring.controller;

import com.harshit.monitoring.config.RateLimitConfig;
import com.harshit.monitoring.service.MonitoringService;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
public class RateLimitController {

    private final MonitoringService monitoringService;

    private final RateLimitConfig rateLimitConfig;

    public RateLimitController(MonitoringService monitoringService, RateLimitConfig rateLimitConfig) {
        this.monitoringService = monitoringService;
        this.rateLimitConfig = rateLimitConfig;
    }

    @GetMapping("/monitor/rate-limit")
    public Map<String, Object> getRateLimitStats() {

        MonitoringService.Accounting counts = monitoringService.getAccounting();

        return Map.of(
                "enabled", true,
                "limit", rateLimitConfig.getDefaultMaxRequests(),
                "windowSeconds", rateLimitConfig.getDefaultWindowSeconds(),
                "blockedRequests", counts.blocked(),
                "attemptedRequests", counts.attempted(),
                "blockPercentage", counts.blockedPercentage()
        );
    }
}
