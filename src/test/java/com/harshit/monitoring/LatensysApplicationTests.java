package com.harshit.monitoring;

import com.harshit.monitoring.config.RateLimitConfig;
import com.harshit.monitoring.repository.ApiRequestLogRepository;
import com.harshit.monitoring.service.LatencySampleStore;
import com.harshit.monitoring.service.RedisRateLimiter;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.redis.connection.RedisConnection;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.hamcrest.Matchers.closeTo;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@Testcontainers
@AutoConfigureMockMvc
@Import(LatensysApplicationTests.ThrowingController.class)
@SpringBootTest(properties = {
        "alerts.enabled=false", "alerts.slack.webhook=", "alerts.discord.webhook=",
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

    @Autowired ApiRequestLogRepository repository;
    @Autowired StringRedisTemplate redisTemplate;
    @Autowired LettuceConnectionFactory redisConnectionFactory;
    @Autowired RateLimitConfig rateLimitConfig;
    @Autowired MockMvc mvc;

    @BeforeEach
    void resetData() {
        repository.deleteAll();
        try (RedisConnection connection = redisTemplate.getConnectionFactory().getConnection()) {
            connection.serverCommands().flushDb();
        }
    }

    @Test
    void contextLoadsWithIsolatedInfrastructure() {
        assertEquals(0, repository.count());
        redisTemplate.opsForValue().set("m1:startup", "ready");
        assertEquals("ready", redisTemplate.opsForValue().get("m1:startup"));
    }

    @Test
    void externalConfigurationIsBound() {
        assertEquals(27, rateLimitConfig.getDefaultMaxRequests());
        assertEquals(redis.getMappedPort(6379), redisConnectionFactory.getPort());
    }

    @Test
    void p95SortsNumericallyAndPreservesDuplicates() {
        Clock clock = Clock.fixed(Instant.ofEpochMilli(1_800_000_000_000L), ZoneOffset.UTC);
        LatencySampleStore store = new LatencySampleStore(redisTemplate, clock);
        String uri = "/p95/" + UUID.randomUUID();
        assertNull(store.p95(uri, 5));
        for (int value = 20; value >= 1; value--) store.record(uri, value * 10L);
        assertEquals(190.0, store.p95(uri, 5));
        store.record(uri, 210);
        assertEquals(200.0, store.p95(uri, 5));
        String duplicateUri = "/p95/" + UUID.randomUUID();
        for (int i = 0; i < 19; i++) store.record(duplicateUri, 10);
        store.record(duplicateUri, 500);
        assertEquals(10.0, store.p95(duplicateUri, 5));
    }

    @Test
    void p95RespectsBothWindowsAndExpiration() {
        Instant instant = Instant.ofEpochMilli(1_800_000_000_000L);
        String uri = "/p95/" + UUID.randomUUID();
        new LatencySampleStore(redisTemplate, Clock.fixed(instant.minusSeconds(600), ZoneOffset.UTC))
                .record(uri, 900);
        LatencySampleStore now = new LatencySampleStore(redisTemplate, Clock.fixed(instant, ZoneOffset.UTC));
        now.record(uri, 42);
        assertEquals(42.0, now.p95(uri, 5));
        assertEquals(900.0, now.p95(uri, 15));
        assertNull(new LatencySampleStore(redisTemplate,
                Clock.fixed(instant.plusSeconds(300), ZoneOffset.UTC)).p95(uri, 5));
        assertNull(new LatencySampleStore(redisTemplate,
                Clock.fixed(instant.plusSeconds(901), ZoneOffset.UTC)).p95(uri, 15));
        assertThrows(IllegalArgumentException.class, () -> now.p95(uri, 10));
    }

    @Test
    void atomicLimiterHandlesConcurrentAndSameMillisecondRequests() throws Exception {
        Clock clock = Clock.fixed(Instant.ofEpochMilli(1_800_000_000_000L), ZoneOffset.UTC);
        RedisRateLimiter limiter = new RedisRateLimiter(redisTemplate, clock);
        String client = UUID.randomUUID().toString();
        ExecutorService pool = Executors.newFixedThreadPool(12);
        CountDownLatch start = new CountDownLatch(1);
        try {
            List<Future<RedisRateLimiter.Decision>> futures = new ArrayList<>();
            for (int i = 0; i < 30; i++) {
                futures.add(pool.submit(() -> { start.await(); return limiter.check(client, "/concurrent", 7, 60); }));
            }
            start.countDown();
            long accepted = 0;
            for (Future<RedisRateLimiter.Decision> future : futures) {
                if (future.get().allowed()) accepted++;
            }
            assertEquals(7, accepted);
            assertEquals(60_000, limiter.check(client, "/concurrent", 7, 60).retryAfterMillis());
            assertTrue(limiter.check(client, "/other", 7, 60).allowed());
            assertTrue(limiter.check("other-client", "/concurrent", 7, 60).allowed());
            RedisRateLimiter later = new RedisRateLimiter(redisTemplate,
                    Clock.fixed(clock.instant().plusSeconds(60), ZoneOffset.UTC));
            assertTrue(later.check(client, "/concurrent", 7, 60).allowed());
        } finally {
            pool.shutdownNow();
        }
    }

    @Test
    void httpRequestsAndRateRejectionsAreRecordedOnce() throws Exception {
        mvc.perform(get("/monitor/endpoints?window=10")).andExpect(status().isBadRequest());
        mvc.perform(get("/test/hello")).andExpect(status().isOk());
        mvc.perform(get("/missing-resource")).andExpect(status().isNotFound());
        mvc.perform(get("/monitor/metrics")).andExpect(status().isOk());
        for (int i = 0; i < 9; i++) mvc.perform(get("/test/hello")).andExpect(status().isOk());
        mvc.perform(get("/test/hello")).andExpect(status().isTooManyRequests())
                .andExpect(header().exists("Retry-After"));
        assertEquals(12, repository.countAttempts());
        assertEquals(1, repository.countBlockedRequests());
        assertEquals(11, repository.countAllowedRequests());
        mvc.perform(get("/monitor/rate-limit")).andExpect(status().isOk())
                .andExpect(jsonPath("$.attemptedRequests").value(12))
                .andExpect(jsonPath("$.blockedRequests").value(1))
                .andExpect(jsonPath("$.blockPercentage", closeTo(100.0 / 12, 0.01)));
        mvc.perform(get("/monitor/errors")).andExpect(status().isOk())
                .andExpect(jsonPath("$.allowedRequests").value(11))
                .andExpect(jsonPath("$.clientErrors").value(1))
                .andExpect(jsonPath("$.errorRate", closeTo(100.0 / 11, 0.01)));
    }

    @Test
    void thrownHandlerIsRecordedAsServerError() {
        assertThrows(Exception.class, () -> mvc.perform(get("/test/m1/throws")));
        assertEquals(1, repository.countServerErrors());
        assertEquals(1, repository.countAttempts());
    }

    @RestController
    static class ThrowingController {
        @GetMapping("/test/m1/throws")
        String fail() { throw new IllegalStateException("expected test failure"); }
    }
}
