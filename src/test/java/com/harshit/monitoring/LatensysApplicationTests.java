package com.harshit.monitoring;

import com.harshit.monitoring.config.RateLimitConfig;
import com.harshit.monitoring.repository.ApiRequestLogRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import static org.junit.jupiter.api.Assertions.assertEquals;

@Testcontainers
@SpringBootTest(properties = {
        "alerts.enabled=false",
        "alerts.slack.webhook=",
        "alerts.discord.webhook=",
        "rate-limit.default-max-requests=27"
})
class LatensysApplicationTests {

    @Container
    static final PostgreSQLContainer<?> postgres =
            new PostgreSQLContainer<>(DockerImageName.parse("postgres:16.6-alpine"));

    @Container
    static final GenericContainer<?> redis =
            new GenericContainer<>(DockerImageName.parse("redis:7.4.1-alpine"))
                    .withExposedPorts(6379);

    @DynamicPropertySource
    static void infrastructureProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
        registry.add("spring.data.redis.host", redis::getHost);
        registry.add("spring.data.redis.port", () -> redis.getMappedPort(6379));
        registry.add("spring.data.redis.password", () -> "");
    }

    @Autowired
    ApiRequestLogRepository repository;

    @Autowired
    StringRedisTemplate redisTemplate;

    @Autowired
    LettuceConnectionFactory redisConnectionFactory;

    @Autowired
    RateLimitConfig rateLimitConfig;

    @Test
    void contextLoadsWithIsolatedInfrastructure() {
        assertEquals(0, repository.count());
        redisTemplate.opsForValue().set("m0:startup", "ready");
        assertEquals("ready", redisTemplate.opsForValue().get("m0:startup"));
    }

    @Test
    void externalConfigurationIsBound() {
        assertEquals(27, rateLimitConfig.getDefaultMaxRequests());
        assertEquals(redis.getMappedPort(6379), redisConnectionFactory.getPort());
    }

}
