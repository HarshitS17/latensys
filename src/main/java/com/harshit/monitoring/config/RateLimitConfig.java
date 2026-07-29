package com.harshit.monitoring.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * Centralised rate-limit configuration.
 *
 * Values are driven from application.yml under the `rate-limit` prefix:
 *
 *   rate-limit:
 *     default-max-requests: 50
 *     default-window-seconds: 60
 *
 * Both RateLimitingFilter and RateLimitController inject this bean so
 * there is a single source of truth — no more mismatch between what the
 * dashboard reports and what the filter actually enforces.
 */
@Component
@ConfigurationProperties(prefix = "rate-limit")
public class RateLimitConfig {

    /** Maximum requests allowed in the time window (default 50). */
    private int defaultMaxRequests = 50;

    /** Rolling window size in seconds (default 60). */
    private int defaultWindowSeconds = 60;

    public int getDefaultMaxRequests() {
        return defaultMaxRequests;
    }

    public void setDefaultMaxRequests(int defaultMaxRequests) {
        this.defaultMaxRequests = defaultMaxRequests;
    }

    public int getDefaultWindowSeconds() {
        return defaultWindowSeconds;
    }

    public void setDefaultWindowSeconds(int defaultWindowSeconds) {
        this.defaultWindowSeconds = defaultWindowSeconds;
    }
}
