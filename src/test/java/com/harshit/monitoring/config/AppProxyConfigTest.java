package com.harshit.monitoring.config;

import java.net.URI;
import java.util.List;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertThrows;

class AppProxyConfigTest {
    @Test
    void originCannotContainCredentialsOrAPath() {
        AppProxyConfig config = new AppProxyConfig();
        config.setAllowedPaths(List.of("/hello"));
        config.setUpstreamUrl(URI.create("http://user:pass@localhost:8081"));
        assertThrows(IllegalArgumentException.class, config::validate);
        config.setUpstreamUrl(URI.create("http://localhost:8081/other"));
        assertThrows(IllegalArgumentException.class, config::validate);
    }
}
