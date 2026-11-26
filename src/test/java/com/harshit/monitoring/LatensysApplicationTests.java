package com.harshit.monitoring;

import com.harshit.monitoring.config.RateLimitConfig;
import com.harshit.monitoring.config.AppProxyConfig;
import com.harshit.monitoring.controller.AppProxyController;
import com.sun.net.httpserver.HttpServer;
import java.net.InetSocketAddress;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.io.IOException;
import java.util.concurrent.atomic.AtomicInteger;
import com.harshit.monitoring.entity.ApiRequestLog;
import com.harshit.monitoring.repository.ApiRequestLogRepository;
import com.harshit.monitoring.service.LatencySampleStore;
import com.harshit.monitoring.service.RedisRateLimiter;
import com.harshit.monitoring.service.MonitoringService;
import com.harshit.monitoring.service.RequestLogRetention;
import com.harshit.monitoring.service.AlertService;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.ZoneId;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.Arrays;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import jakarta.persistence.EntityManagerFactory;
import org.hibernate.SessionFactory;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.SpyBean;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.data.redis.connection.RedisConnection;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.security.test.context.support.WithAnonymousUser;
import org.springframework.security.test.context.TestSecurityContextHolder;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpSession;
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
import static org.mockito.Mockito.verifyNoInteractions;
import static org.hamcrest.Matchers.closeTo;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@Testcontainers
@AutoConfigureMockMvc
@WithMockUser(username = "admin", roles = "ADMIN")
@Import(LatensysApplicationTests.ThrowingController.class)
@SpringBootTest(properties = {
        "alerts.enabled=false", "alerts.slack.webhook=", "alerts.discord.webhook=",
        "rate-limit.default-max-requests=27", "retention.enabled=false", "app.request-timeout=500ms",
        "app.max-response-bytes=256", "app.allowed-paths=/hello,/fail,/echo,/stats,/slow,/large,/drip",
        "security.admin.username=admin",
        "security.admin.password-hash=$2y$10$CcpVfYYGx2.BEzGxVJ/2xudb2CV24UMbqZp0VJVtedZPnK8ph9d3S"
})
class LatensysApplicationTests {
    static final AtomicInteger upstreamHelloCalls = new AtomicInteger();
    static final HttpServer upstream = startUpstream();

    static HttpServer startUpstream() {
        try {
            HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
            server.createContext("/hello", exchange -> {
                upstreamHelloCalls.incrementAndGet();
                reply(exchange, 200, "{\"source\":\"independent-upstream\"}", "application/json");
            });
            server.createContext("/fail", exchange -> reply(exchange, 500,
                    "{\"error\":\"upstream_failure\"}", "application/json"));
            server.createContext("/echo", exchange -> reply(exchange, 201,
                    new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8), "text/plain"));
            server.createContext("/slow", exchange -> {
                try { Thread.sleep(1500); } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
                reply(exchange, 200, "late", "text/plain");
            });
            server.createContext("/large", exchange -> reply(exchange, 200, "x".repeat(300), "text/plain"));
            server.createContext("/drip", exchange -> {
                exchange.sendResponseHeaders(200, 4);
                try { Thread.sleep(1500); } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
                try (var output = exchange.getResponseBody()) { output.write("late".getBytes(StandardCharsets.UTF_8)); }
            });
            server.setExecutor(Executors.newCachedThreadPool(task -> {
                Thread thread = new Thread(task, "test-upstream");
                thread.setDaemon(true);
                return thread;
            }));
            server.start();
            return server;
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
    }

    static void reply(com.sun.net.httpserver.HttpExchange exchange, int status, String body, String contentType)
            throws IOException {
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().add("Content-Type", contentType);
        exchange.sendResponseHeaders(status, bytes.length);
        try (var output = exchange.getResponseBody()) { output.write(bytes); }
    }

    @AfterAll
    static void stopUpstream() { upstream.stop(0); }

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
        registry.add("app.upstream-url", () -> "http://127.0.0.1:" + upstream.getAddress().getPort());
    }

    @Autowired ApiRequestLogRepository repository;
    @Autowired StringRedisTemplate redisTemplate;
    @Autowired LettuceConnectionFactory redisConnectionFactory;
    @Autowired RateLimitConfig rateLimitConfig;
    @Autowired MockMvc mvc;
    @Autowired MonitoringService monitoringService;
    @Autowired EntityManagerFactory entityManagerFactory;
    @Autowired JdbcTemplate jdbcTemplate;
    @SpyBean AlertService alertService;

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
    void continuousLatencyTrafficRemovesExpiredMembersAndKeepsKeyTtl() {
        Instant instant = Instant.ofEpochMilli(1_800_000_000_000L);
        String uri = "/p95/" + UUID.randomUUID();
        new LatencySampleStore(redisTemplate, Clock.fixed(instant, ZoneOffset.UTC)).record(uri, 50);
        new LatencySampleStore(redisTemplate, Clock.fixed(instant.plusSeconds(901), ZoneOffset.UTC))
                .record(uri, 75);
        assertEquals(1, redisTemplate.opsForZSet().zCard("latency:v2:" + uri));
        assertTrue(redisTemplate.getExpire("latency:v2:" + uri) > 0);
    }

    @Test
    void retentionDeletesOnlyOldRowsInBoundedBatches() {
        LocalDateTime now = LocalDateTime.now().truncatedTo(ChronoUnit.MICROS);
        Clock fixed = Clock.fixed(now.atZone(ZoneId.systemDefault()).toInstant(), ZoneId.systemDefault());
        repository.save(new ApiRequestLog("GET", "/old-one", 200, 10, "ALLOWED", now.minusDays(31)));
        repository.save(new ApiRequestLog("GET", "/old-two", 200, 10, "ALLOWED", now.minusDays(31)));
        repository.save(new ApiRequestLog("GET", "/old-three", 200, 10, "ALLOWED", now.minusDays(31)));
        repository.save(new ApiRequestLog("GET", "/boundary", 200, 10, "ALLOWED", now.minusDays(30)));
        repository.save(new ApiRequestLog("GET", "/recent", 200, 10, "ALLOWED", now.minusDays(1)));
        assertEquals(1, monitoringService.getAccounting().attempted());
        RequestLogRetention retention = new RequestLogRetention(repository, fixed, 30, 2);
        assertEquals(2, retention.deleteOneBatch());
        assertEquals(1, retention.deleteOneBatch());
        assertEquals(0, retention.deleteOneBatch());
        assertEquals(2, repository.count());
        assertTrue(repository.findAll().stream().anyMatch(row -> row.getUri().equals("/boundary")));
        assertTrue(repository.findAll().stream().anyMatch(row -> row.getUri().equals("/recent")));
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
        assertEquals(12, repository.count());
        assertEquals(1, monitoringService.getAccounting().blocked());
        assertEquals(11, monitoringService.getAccounting().allowed());
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
        assertEquals(1, monitoringService.getAccounting().serverErrors());
        assertEquals(1, repository.count());
    }

    @Test
    void dashboardOptimizedMeasurement() throws Exception {
        List<ApiRequestLog> rows = new ArrayList<>();
        for (int endpoint = 0; endpoint < 10; endpoint++) {
            for (int request = 0; request < 20; request++) {
                rows.add(new ApiRequestLog("GET", "/bench/" + endpoint, 200, 20 + request));
            }
        }
        repository.saveAll(rows);
        for (int i = 0; i < 3; i++) runOptimizedRefresh();
        org.hibernate.stat.Statistics statistics = entityManagerFactory.unwrap(SessionFactory.class).getStatistics();
        statistics.setStatisticsEnabled(true);
        statistics.clear();
        long[] nanos = new long[20];
        for (int i = 0; i < nanos.length; i++) {
            long start = System.nanoTime();
            runOptimizedRefresh();
            nanos[i] = System.nanoTime() - start;
        }
        Arrays.sort(nanos);
        assertEquals(5L * nanos.length, statistics.getPrepareStatementCount());
        System.out.println("OPTIMIZED query_count_per_refresh=" + statistics.getPrepareStatementCount() / nanos.length
                + " p50_ms=" + nanos[9] / 1_000_000.0
                + " p95_ms=" + nanos[18] / 1_000_000.0);
        explain("endpoint_group", "SELECT uri, COUNT(*), AVG(response_time) FROM api_request_logs "
                + "WHERE timestamp >= now() - interval '5 minutes' AND decision = 'ALLOWED' GROUP BY uri");
        explain("minute_group", "SELECT date_trunc('minute', timestamp), COUNT(*) FROM api_request_logs "
                + "WHERE timestamp >= now() - interval '15 minutes' GROUP BY 1");
        explain("retention_lookup", "SELECT id FROM api_request_logs "
                + "WHERE timestamp < now() - interval '30 days' ORDER BY timestamp, id LIMIT 5000");
    }

    @Test
    void endpointQueryCountDoesNotGrowWithEndpointCount() throws Exception {
        org.hibernate.stat.Statistics statistics = entityManagerFactory.unwrap(SessionFactory.class).getStatistics();
        statistics.setStatisticsEnabled(true);
        repository.save(new ApiRequestLog("GET", "/bench/0", 200, 20));
        statistics.clear();
        mvc.perform(get("/monitor/endpoints?window=5")).andExpect(status().isOk());
        long oneEndpoint = statistics.getPrepareStatementCount();
        for (int i = 1; i < 10; i++) repository.save(new ApiRequestLog("GET", "/bench/" + i, 200, 20));
        statistics.clear();
        mvc.perform(get("/monitor/endpoints?window=5")).andExpect(status().isOk());
        assertEquals(1, oneEndpoint);
        assertEquals(oneEndpoint, statistics.getPrepareStatementCount());
    }

    @Test
    void dashboardReadsDoNotEvaluateOrDeliverAlerts() throws Exception {
        runOptimizedRefresh();
        mvc.perform(get("/monitor/errors")).andExpect(status().isOk());
        verifyNoInteractions(alertService);
    }

    @Test
    @WithAnonymousUser
    void anonymousManagementAccessIsDeniedButHealthIsPublic() throws Exception {
        mvc.perform(get("/")).andExpect(status().is3xxRedirection());
        mvc.perform(get("/monitor/metrics")).andExpect(status().isUnauthorized());
        mvc.perform(get("/actuator/metrics")).andExpect(status().isUnauthorized());
        mvc.perform(get("/test/alerts/all")).andExpect(status().isUnauthorized());
        mvc.perform(get("/actuator/health")).andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("UP"));
        mvc.perform(get("/login")).andExpect(status().isOk());
        assertEquals(0, repository.count());
    }

    @Test
    @WithAnonymousUser
    void loginFailureSuccessAndLogoutUseSessionAndCsrf() throws Exception {
        mvc.perform(post("/login").param("username", "admin").param("password", "wrong")
                .with(csrf())).andExpect(status().is3xxRedirection())
                .andExpect(header().string("Location", "/login?error"));
        var login = mvc.perform(post("/login").param("username", "admin")
                .param("password", "test-password").with(csrf()))
                .andExpect(status().is3xxRedirection()).andReturn();
        MockHttpSession session = (MockHttpSession) login.getRequest().getSession(false);
        TestSecurityContextHolder.clearContext();
        mvc.perform(get("/monitor/metrics").session(session)).andExpect(status().isOk());
        mvc.perform(post("/logout").session(session)).andExpect(status().isForbidden());
        mvc.perform(post("/logout").session(session).with(csrf()))
                .andExpect(status().is3xxRedirection());
        mvc.perform(get("/monitor/metrics").session(session)).andExpect(status().isUnauthorized());
        assertEquals(0, repository.count());
    }

    @Test
    void proxyPreservesRealUpstreamResponsesAndAccountsForThem() throws Exception {
        mvc.perform(get("/app/hello")).andExpect(status().isOk())
                .andExpect(jsonPath("$.source").value("independent-upstream"));
        mvc.perform(get("/app/fail")).andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.error").value("upstream_failure"));
        mvc.perform(post("/app/echo").contentType("text/plain").content("echoed"))
                .andExpect(status().isCreated())
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.content().string("echoed"));
        mvc.perform(get("/monitor/metrics")).andExpect(status().isOk())
                .andExpect(jsonPath("$.attemptedRequests").value(3))
                .andExpect(jsonPath("$.serverErrors").value(1));
        mvc.perform(get("/monitor/endpoints?window=5")).andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.uri == '/app/hello')]").exists());
    }

    @Test
    void proxyRateLimitStopsRequestsBeforeTheyReachUpstream() throws Exception {
        int before = upstreamHelloCalls.get();
        for (int i = 0; i < 5; i++) mvc.perform(get("/app/hello")).andExpect(status().isOk());
        mvc.perform(get("/app/hello")).andExpect(status().isTooManyRequests())
                .andExpect(header().exists("Retry-After"));
        assertEquals(before + 5, upstreamHelloCalls.get());
        mvc.perform(get("/monitor/metrics")).andExpect(status().isOk())
                .andExpect(jsonPath("$.attemptedRequests").value(6))
                .andExpect(jsonPath("$.allowedRequests").value(5))
                .andExpect(jsonPath("$.blockedRequests").value(1));
    }

    @Test
    void proxyBlocksUnlistedPathsAndArbitraryDestinations() throws Exception {
        mvc.perform(get("/app/unlisted")).andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error").value("route_not_allowed"));
        mvc.perform(get("/app/hello?url=http://127.0.0.1:1"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.source").value("independent-upstream"));
    }

    @Test
    void proxyReportsTimeoutAndConnectionFailure() throws Exception {
        mvc.perform(get("/app/slow")).andExpect(status().isGatewayTimeout())
                .andExpect(jsonPath("$.error").value("upstream_timeout"));
        mvc.perform(get("/app/drip")).andExpect(status().isGatewayTimeout())
                .andExpect(jsonPath("$.error").value("upstream_timeout"));
        AppProxyConfig unavailable = new AppProxyConfig();
        unavailable.setUpstreamUrl(URI.create("http://127.0.0.1:1"));
        unavailable.setAllowedPaths(List.of("/hello"));
        unavailable.validate();
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/app/hello");
        assertEquals(502, new AppProxyController(unavailable).forward(request).getStatusCode().value());
    }

    @Test
    void proxyBoundsRequestAndResponseBuffers() throws Exception {
        mvc.perform(post("/app/echo").contentType("text/plain").content(new byte[65537]))
                .andExpect(status().isPayloadTooLarge());
        mvc.perform(get("/app/large")).andExpect(status().isBadGateway())
                .andExpect(jsonPath("$.error").value("upstream_response_too_large"));
    }

    private void explain(String label, String sql) {
        for (String line : jdbcTemplate.queryForList("EXPLAIN (ANALYZE, BUFFERS) " + sql, String.class)) {
            System.out.println("EXPLAIN " + label + " " + line);
        }
    }

    private void runOptimizedRefresh() throws Exception {
        for (String path : List.of("/monitor/metrics", "/monitor/rate-limit",
                "/monitor/endpoints?window=5", "/monitor/rpm", "/monitor/blocked-trend")) {
            mvc.perform(get(path)).andExpect(status().isOk());
        }
    }

    @RestController
    static class ThrowingController {
        @GetMapping("/test/m1/throws")
        String fail() { throw new IllegalStateException("expected test failure"); }
    }
}
