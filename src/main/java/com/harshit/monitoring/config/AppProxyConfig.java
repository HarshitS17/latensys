package com.harshit.monitoring.config;

import jakarta.annotation.PostConstruct;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;
import java.net.URI;
import java.time.Duration;
import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;
import org.springframework.validation.annotation.Validated;

@Component
@Validated
@ConfigurationProperties(prefix = "app")
public class AppProxyConfig {
    private URI upstreamUrl;
    private Duration connectTimeout = Duration.ofSeconds(2);
    private Duration requestTimeout = Duration.ofSeconds(5);
    @Min(1) private int maxRequestBytes = 65536;
    @Min(1) private int maxResponseBytes = 1048576;
    @NotEmpty @Size(max = 100) private List<String> allowedPaths = List.of();

    @PostConstruct
    public void validate() {
        if (upstreamUrl == null || !("http".equals(upstreamUrl.getScheme()) || "https".equals(upstreamUrl.getScheme()))
                || upstreamUrl.getHost() == null || upstreamUrl.getUserInfo() != null
                || upstreamUrl.getRawQuery() != null || upstreamUrl.getRawFragment() != null
                || !(upstreamUrl.getRawPath() == null || upstreamUrl.getRawPath().isEmpty()
                || "/".equals(upstreamUrl.getRawPath()))) {
            throw new IllegalArgumentException("app.upstream-url must be a fixed HTTP(S) origin");
        }
        if (connectTimeout == null || connectTimeout.isNegative() || connectTimeout.isZero()
                || requestTimeout == null || requestTimeout.isNegative() || requestTimeout.isZero()) {
            throw new IllegalArgumentException("App proxy timeouts must be positive");
        }
        for (String path : allowedPaths) {
            if (path == null || !path.matches("/[A-Za-z0-9/_-]{1,180}") || path.contains("//")) {
                throw new IllegalArgumentException("App proxy allowed paths must be bounded exact paths");
            }
        }
    }

    public URI getUpstreamUrl() { return upstreamUrl; }
    public void setUpstreamUrl(URI value) { upstreamUrl = value; }
    public Duration getConnectTimeout() { return connectTimeout; }
    public void setConnectTimeout(Duration value) { connectTimeout = value; }
    public Duration getRequestTimeout() { return requestTimeout; }
    public void setRequestTimeout(Duration value) { requestTimeout = value; }
    public int getMaxRequestBytes() { return maxRequestBytes; }
    public void setMaxRequestBytes(int value) { maxRequestBytes = value; }
    public int getMaxResponseBytes() { return maxResponseBytes; }
    public void setMaxResponseBytes(int value) { maxResponseBytes = value; }
    public List<String> getAllowedPaths() { return allowedPaths; }
    public void setAllowedPaths(List<String> value) { allowedPaths = value; }
}
