package com.harshit.monitoring.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.Map;

/**
 * AlertService - Send notifications when metrics exceed thresholds
 *
 * Supports:
 * - Slack webhooks
 * - Discord webhooks
 * - Console logging (for testing)
 *
 * Alerts trigger on:
 * - High error rate (>10%)
 * - Slow endpoints (P95 > 1000ms)
 * - High traffic (RPM > 1000)
 * - Rate limiting active (>50 blocked/min)
 */
@Service
public class AlertService {

    private final RestTemplate restTemplate = new RestTemplate();

    @Value("${alerts.slack.webhook:}")
    private String slackWebhook;

    @Value("${alerts.discord.webhook:}")
    private String discordWebhook;

    @Value("${alerts.enabled:false}")
    private boolean alertsEnabled;

    // Cooldown to prevent spam (5 minutes)
    private final Map<String, Long> lastAlertTime = new HashMap<>();
    private static final long COOLDOWN_MS = 5 * 60 * 1000;

    /**
     * Send an alert with severity level
     *
     * @param severity LOW, MEDIUM, HIGH, CRITICAL
     * @param title Short title of the issue
     * @param message Detailed message
     */
    public void sendAlert(String severity, String title, String message) {
        if (!alertsEnabled) {
            System.out.println("[ALERT DISABLED] " + severity + ": " + title);
            return;
        }

        // Check cooldown to prevent spam
        String key = severity + ":" + title;
        Long lastTime = lastAlertTime.get(key);
        long now = System.currentTimeMillis();

        if (lastTime != null && (now - lastTime) < COOLDOWN_MS) {
            System.out.println("[ALERT SUPPRESSED - COOLDOWN] " + title);
            return;
        }

        lastAlertTime.put(key, now);

        String timestamp = LocalDateTime.now()
                .format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));

        String emoji = getEmojiForSeverity(severity);
        String color = getColorForSeverity(severity);

        // Log to console always
        System.err.println("\n" + "=".repeat(60));
        System.err.println(emoji + " ALERT [" + severity + "] - " + timestamp);
        System.err.println("Title: " + title);
        System.err.println("Message: " + message);
        System.err.println("=".repeat(60) + "\n");

        // Send to Slack
        if (slackWebhook != null && !slackWebhook.isEmpty()) {
            sendSlackAlert(severity, title, message, emoji, color, timestamp);
        }

        // Send to Discord
        if (discordWebhook != null && !discordWebhook.isEmpty()) {
            sendDiscordAlert(severity, title, message, emoji, color, timestamp);
        }
    }

    /**
     * Send alert to Slack webhook
     */
    private void sendSlackAlert(String severity, String title, String message,
                                String emoji, String color, String timestamp) {
        try {
            Map<String, Object> payload = new HashMap<>();

            // Create attachment
            Map<String, Object> attachment = new HashMap<>();
            attachment.put("color", color);
            attachment.put("title", emoji + " " + title);
            attachment.put("text", message);
            attachment.put("footer", "Latensys Monitoring | " + timestamp);

            Map<String, String> field = new HashMap<>();
            field.put("title", "Severity");
            field.put("value", severity);
            field.put("short", "true");

            attachment.put("fields", new Object[]{field});
            payload.put("attachments", new Object[]{attachment});

            restTemplate.postForObject(slackWebhook, payload, String.class);
            System.out.println("✅ Slack alert sent successfully");

        } catch (Exception e) {
            System.err.println("❌ Failed to send Slack alert: " + e.getMessage());
        }
    }

    /**
     * Send alert to Discord webhook
     */
    private void sendDiscordAlert(String severity, String title, String message,
                                  String emoji, String color, String timestamp) {
        try {
            Map<String, Object> payload = new HashMap<>();

            // Create embed
            Map<String, Object> embed = new HashMap<>();
            embed.put("title", emoji + " " + title);
            embed.put("description", message);
            embed.put("color", Integer.parseInt(color.substring(1), 16)); // Convert hex to int
            embed.put("footer", Map.of("text", "Latensys Monitoring"));
            embed.put("timestamp", LocalDateTime.now().toString());

            Map<String, Object> field = new HashMap<>();
            field.put("name", "Severity");
            field.put("value", severity);
            field.put("inline", true);

            embed.put("fields", new Object[]{field});
            payload.put("embeds", new Object[]{embed});

            restTemplate.postForObject(discordWebhook, payload, String.class);
            System.out.println("✅ Discord alert sent successfully");

        } catch (Exception e) {
            System.err.println("❌ Failed to send Discord alert: " + e.getMessage());
        }
    }

    /**
     * Get emoji for severity level
     */
    private String getEmojiForSeverity(String severity) {
        return switch (severity.toUpperCase()) {
            case "CRITICAL" -> "🚨";
            case "HIGH" -> "⚠️";
            case "MEDIUM" -> "⚡";
            case "LOW" -> "ℹ️";
            default -> "📢";
        };
    }

    /**
     * Get color code for severity level
     */
    private String getColorForSeverity(String severity) {
        return switch (severity.toUpperCase()) {
            case "CRITICAL" -> "#dc2626"; // Red
            case "HIGH" -> "#f59e0b";     // Orange
            case "MEDIUM" -> "#eab308";   // Yellow
            case "LOW" -> "#3b82f6";      // Blue
            default -> "#6b7280";         // Gray
        };
    }

    /**
     * Convenience methods for specific alert types
     */

    public void alertHighErrorRate(double errorRate) {
        sendAlert("HIGH",
                "High Error Rate Detected",
                String.format("Current error rate: %.2f%% (Threshold: 10%%)", errorRate)
        );
    }

    public void alertSlowEndpoint(String uri, double p95Latency) {
        sendAlert("MEDIUM",
                "Slow Endpoint Detected",
                String.format("Endpoint: %s\nP95 Latency: %.0f ms (Threshold: 1000ms)", uri, p95Latency)
        );
    }

    public void alertHighTraffic(long requestsPerMinute) {
        sendAlert("LOW",
                "High Traffic Volume",
                String.format("Current RPM: %d (Threshold: 1000)", requestsPerMinute)
        );
    }

    public void alertRateLimitingActive(long blockedRequests) {
        sendAlert("MEDIUM",
                "Rate Limiting Active",
                String.format("Blocked %d requests in the last minute", blockedRequests)
        );
    }

    public void alertSystemHealth(String component, String status) {
        sendAlert("CRITICAL",
                component + " Health Check Failed",
                String.format("Component: %s\nStatus: %s", component, status)
        );
    }
}