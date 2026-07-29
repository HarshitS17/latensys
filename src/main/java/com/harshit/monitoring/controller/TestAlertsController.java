package com.harshit.monitoring.controller;

import com.harshit.monitoring.service.AlertService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * TestAlertsController - Endpoints to test the alerting system
 */
@RestController
@RequestMapping("/test/alerts")
public class TestAlertsController {

    private final AlertService alertService;

    public TestAlertsController(AlertService alertService) {
        this.alertService = alertService;
    }

    /**
     * Test all alert types at once
     */
    @GetMapping("/all")
    public Map<String, String> testAllAlerts() {
        alertService.alertHighErrorRate(15.5);
        alertService.alertSlowEndpoint("/api/slow-endpoint", 1250.0);
        alertService.alertHighTraffic(1500);
        alertService.alertRateLimitingActive(75);

        return Map.of(
                "status", "success",
                "message", "Test alerts sent! Check your Slack/Discord."
        );
    }

    /**
     * Test high error rate alert
     */
    @GetMapping("/error-rate")
    public Map<String, String> testErrorRateAlert() {
        alertService.alertHighErrorRate(15.5);
        return Map.of("status", "success", "alert", "High Error Rate");
    }

    /**
     * Test slow endpoint alert
     */
    @GetMapping("/slow-endpoint")
    public Map<String, String> testSlowEndpointAlert() {
        alertService.alertSlowEndpoint("/api/users/search", 1250.0);
        return Map.of("status", "success", "alert", "Slow Endpoint");
    }

    /**
     * Test high traffic alert
     */
    @GetMapping("/high-traffic")
    public Map<String, String> testHighTrafficAlert() {
        alertService.alertHighTraffic(1500);
        return Map.of("status", "success", "alert", "High Traffic");
    }

    /**
     * Test rate limiting alert
     */
    @GetMapping("/rate-limiting")
    public Map<String, String> testRateLimitingAlert() {
        alertService.alertRateLimitingActive(75);
        return Map.of("status", "success", "alert", "Rate Limiting Active");
    }

    /**
     * Test custom alert with all severity levels
     */
    @GetMapping("/severities")
    public Map<String, String> testSeverities() {
        alertService.sendAlert("LOW", "Low Priority Test", "This is a low priority alert");
        alertService.sendAlert("MEDIUM", "Medium Priority Test", "This is a medium priority alert");
        alertService.sendAlert("HIGH", "High Priority Test", "This is a high priority alert");
        alertService.sendAlert("CRITICAL", "Critical Priority Test", "This is a critical alert");

        return Map.of(
                "status", "success",
                "message", "Sent alerts with all severity levels"
        );
    }
}