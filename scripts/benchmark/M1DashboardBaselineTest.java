package com.harshit.monitoring;

import com.harshit.monitoring.entity.ApiRequestLog;
import com.harshit.monitoring.repository.ApiRequestLogRepository;
import jakarta.persistence.EntityManagerFactory;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import org.hibernate.SessionFactory;
import org.hibernate.stat.Statistics;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Archived M1 benchmark harness. Copy into M1 src/test/java/com/harshit/monitoring/ to rerun. */
@Testcontainers
@AutoConfigureMockMvc
@SpringBootTest(properties = {"alerts.enabled=false", "alerts.slack.webhook=",
        "alerts.discord.webhook="})
class M1DashboardBaselineTest {
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
    @Autowired EntityManagerFactory entityManagerFactory;
    @Autowired MockMvc mvc;

    @Test
    void dashboardBaselineMeasurement() throws Exception {
        List<ApiRequestLog> rows = new ArrayList<>();
        for (int endpoint = 0; endpoint < 10; endpoint++) {
            for (int request = 0; request < 20; request++) {
                rows.add(new ApiRequestLog("GET", "/bench/" + endpoint, 200, 20 + request));
            }
        }
        repository.saveAll(rows);
        for (int i = 0; i < 3; i++) refresh();
        Statistics statistics = entityManagerFactory.unwrap(SessionFactory.class).getStatistics();
        statistics.setStatisticsEnabled(true);
        statistics.clear();
        long[] nanos = new long[20];
        for (int i = 0; i < nanos.length; i++) {
            long start = System.nanoTime();
            refresh();
            nanos[i] = System.nanoTime() - start;
        }
        Arrays.sort(nanos);
        assertEquals(118L * nanos.length, statistics.getPrepareStatementCount());
        System.out.println("BASELINE query_count_per_refresh="
                + statistics.getPrepareStatementCount() / nanos.length
                + " p50_ms=" + nanos[9] / 1_000_000.0
                + " p95_ms=" + nanos[18] / 1_000_000.0);
    }

    private void refresh() throws Exception {
        for (String path : List.of("/monitor/metrics", "/monitor/errors", "/monitor/rate-limit",
                "/monitor/endpoints?window=5", "/monitor/rpm", "/monitor/rpm",
                "/monitor/blocked-trend", "/monitor/blocked-trend", "/monitor/blocked-trend")) {
            mvc.perform(get(path)).andExpect(status().isOk());
        }
    }
}
