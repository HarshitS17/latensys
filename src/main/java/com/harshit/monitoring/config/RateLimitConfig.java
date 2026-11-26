package com.harshit.monitoring.config;

import jakarta.annotation.PostConstruct;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;
import org.springframework.validation.annotation.Validated;

@Component
@Validated
@ConfigurationProperties(prefix = "rate-limit")
public class RateLimitConfig {
    @Min(1) private int defaultMaxRequests = 50;
    @Min(1) private int defaultWindowSeconds = 60;
    @NotNull @Size(max = 100) private Map<String, @Valid Policy> overrides = new LinkedHashMap<>();

    public static class Policy {
        @Min(1) private int maxRequests;
        @Min(1) private int windowSeconds;
        public int getMaxRequests() { return maxRequests; }
        public void setMaxRequests(int value) { maxRequests = value; }
        public int getWindowSeconds() { return windowSeconds; }
        public void setWindowSeconds(int value) { windowSeconds = value; }
    }

    @PostConstruct
    public void validatePaths() {
        for (String path : overrides.keySet()) {
            if (path == null || !path.startsWith("/") || path.length() > 200 || path.contains("*")
                    || path.contains("?") || path.contains("#") || path.contains("//")) {
                throw new IllegalArgumentException("Rate-limit overrides require exact, bounded paths");
            }
        }
    }

    public Policy resolve(String path) {
        Policy override = overrides.get(path);
        if (override != null) return override;
        Policy fallback = new Policy();
        fallback.setMaxRequests(defaultMaxRequests);
        fallback.setWindowSeconds(defaultWindowSeconds);
        return fallback;
    }

    public int getDefaultMaxRequests() { return defaultMaxRequests; }
    public void setDefaultMaxRequests(int value) { defaultMaxRequests = value; }
    public int getDefaultWindowSeconds() { return defaultWindowSeconds; }
    public void setDefaultWindowSeconds(int value) { defaultWindowSeconds = value; }
    public Map<String, Policy> getOverrides() { return overrides; }
    public void setOverrides(Map<String, Policy> value) { overrides = value; }
}
