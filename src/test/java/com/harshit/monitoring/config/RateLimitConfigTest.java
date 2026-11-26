package com.harshit.monitoring.config;

import jakarta.validation.Validation;
import java.util.Map;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RateLimitConfigTest {
    @Test
    void exactOverrideTakesPrecedenceOverDefault() {
        RateLimitConfig config = new RateLimitConfig();
        config.setDefaultMaxRequests(50);
        config.setDefaultWindowSeconds(60);
        RateLimitConfig.Policy override = new RateLimitConfig.Policy();
        override.setMaxRequests(5);
        override.setWindowSeconds(30);
        config.setOverrides(Map.of("/app/hello", override));
        config.validatePaths();
        assertEquals(5, config.resolve("/app/hello").getMaxRequests());
        assertEquals(30, config.resolve("/app/hello").getWindowSeconds());
        assertEquals(50, config.resolve("/app/hello/extra").getMaxRequests());
        assertEquals(60, config.resolve("/app/hello/extra").getWindowSeconds());
    }

    @Test
    void invalidNumbersAndPathsAreRejected() {
        RateLimitConfig config = new RateLimitConfig();
        config.setDefaultMaxRequests(0);
        RateLimitConfig.Policy override = new RateLimitConfig.Policy();
        override.setMaxRequests(-1);
        override.setWindowSeconds(0);
        config.setOverrides(Map.of("/app/hello", override));
        try (var factory = Validation.buildDefaultValidatorFactory()) {
            var violations = factory.getValidator().validate(config);
            assertFalse(violations.isEmpty());
            assertTrue(violations.stream().anyMatch(value -> value.getPropertyPath().toString().contains("overrides")));
        }
        config.setOverrides(Map.of("/app/**", override));
        assertThrows(IllegalArgumentException.class, config::validatePaths);
    }
}
