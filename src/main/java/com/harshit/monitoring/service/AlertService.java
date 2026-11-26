package com.harshit.monitoring.service;

import java.time.Clock;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

@Service
public class AlertService {
    private static final Logger log = LoggerFactory.getLogger(AlertService.class);
    private static final long COOLDOWN_MS = 5 * 60 * 1000;
    private final Map<String, Long> deliveredAt = new HashMap<>();
    private final RestTemplate restTemplate;
    private final Clock clock;
    private final boolean enabled;
    private final String slackWebhook;
    private final String discordWebhook;

    public AlertService(RestTemplate restTemplate, Clock clock,
                        @Value("${alerts.enabled:false}") boolean enabled,
                        @Value("${alerts.slack.webhook:}") String slackWebhook,
                        @Value("${alerts.discord.webhook:}") String discordWebhook) {
        this.restTemplate = restTemplate;
        this.clock = clock;
        this.enabled = enabled;
        this.slackWebhook = slackWebhook;
        this.discordWebhook = discordWebhook;
    }

    /** Serialized delivery avoids duplicate concurrent sends. Cooldown starts only after success. */
    public synchronized boolean sendAlert(String severity, String title, String message) {
        if (!enabled) return false;
        String key = severity + ":" + title;
        long now = clock.millis();
        Long last = deliveredAt.get(key);
        if (last != null && now - last < COOLDOWN_MS) return false;

        String timestamp = LocalDateTime.now(clock).format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
        String emoji = emoji(severity);
        String color = color(severity);
        log.warn("{} ALERT [{}] {}: {}", emoji, severity, title, message);
        boolean hasWebhook = !slackWebhook.isBlank() || !discordWebhook.isBlank();
        boolean delivered = !hasWebhook; // Console is the destination when no webhook is configured.
        if (!slackWebhook.isBlank()) {
            delivered |= post(slackWebhook, slackPayload(severity, title, message, emoji, color, timestamp), "Slack");
        }
        if (!discordWebhook.isBlank()) {
            delivered |= post(discordWebhook, discordPayload(severity, title, message, emoji, color), "Discord");
        }
        if (delivered) deliveredAt.put(key, now);
        return delivered;
    }

    private boolean post(String url, Map<String, Object> payload, String channel) {
        try {
            return restTemplate.postForEntity(url, payload, String.class).getStatusCode().is2xxSuccessful();
        } catch (RestClientException e) {
            log.warn("{} alert delivery failed ({})", channel, e.getClass().getSimpleName());
            return false;
        }
    }

    private Map<String, Object> slackPayload(String severity, String title, String message,
                                              String emoji, String color, String timestamp) {
        return Map.of("attachments", new Object[]{Map.of(
                "color", color, "title", emoji + " " + title, "text", message,
                "footer", "Latensys Monitoring | " + timestamp,
                "fields", new Object[]{Map.of("title", "Severity", "value", severity, "short", true)})});
    }

    private Map<String, Object> discordPayload(String severity, String title, String message,
                                                String emoji, String color) {
        return Map.of("embeds", new Object[]{Map.of(
                "title", emoji + " " + title, "description", message,
                "color", Integer.parseInt(color.substring(1), 16),
                "footer", Map.of("text", "Latensys Monitoring"),
                "timestamp", clock.instant().toString(),
                "fields", new Object[]{Map.of("name", "Severity", "value", severity, "inline", true)})});
    }

    private String emoji(String severity) {
        return switch (severity.toUpperCase()) {
            case "CRITICAL" -> "🚨";
            case "HIGH" -> "⚠️";
            case "MEDIUM" -> "⚡";
            case "LOW" -> "ℹ️";
            default -> "📢";
        };
    }

    private String color(String severity) {
        return switch (severity.toUpperCase()) {
            case "CRITICAL" -> "#dc2626";
            case "HIGH" -> "#f59e0b";
            case "MEDIUM" -> "#eab308";
            case "LOW" -> "#3b82f6";
            default -> "#6b7280";
        };
    }

    public void alertHighErrorRate(double rate) {
        sendAlert("HIGH", "High Error Rate Detected",
                String.format("Current error rate: %.2f%% (Threshold: 10%%)", rate));
    }

    public void alertSlowEndpoint(String uri, double p95) {
        sendAlert("MEDIUM", "Slow Endpoint Detected: " + uri,
                String.format("Endpoint: %s\nP95 Latency: %.0f ms (Threshold: 1000ms)", uri, p95));
    }

    public void alertHighTraffic(long rpm) {
        sendAlert("LOW", "High Traffic Volume",
                String.format("Current RPM: %d (Threshold: 1000)", rpm));
    }

    public void alertRateLimitingActive(long blocked) {
        sendAlert("MEDIUM", "Rate Limiting Active",
                String.format("Blocked %d requests in the last minute", blocked));
    }

    public void alertSystemHealth(String component, String status) {
        sendAlert("CRITICAL", component + " Health Check Failed",
                String.format("Component: %s\nStatus: %s", component, status));
    }
}
