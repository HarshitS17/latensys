package com.harshit.monitoring.service;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import org.junit.jupiter.api.Test;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AlertServiceTest {
    private static final String WEBHOOK = "https://example.invalid/webhook";
    private final Clock clock = Clock.fixed(Instant.parse("2026-11-26T12:00:00Z"), ZoneOffset.UTC);

    @Test
    void failedWebhookDoesNotStartCooldown() {
        RestTemplate http = mock(RestTemplate.class);
        when(http.postForEntity(eq(WEBHOOK), any(), eq(String.class)))
                .thenThrow(new RestClientException("delivery failed"))
                .thenReturn(ResponseEntity.ok("ok"));
        AlertService alerts = new AlertService(http, clock, true, WEBHOOK, "");
        assertFalse(alerts.sendAlert("HIGH", "failure", "message"));
        assertTrue(alerts.sendAlert("HIGH", "failure", "message"));
        assertFalse(alerts.sendAlert("HIGH", "failure", "message"));
        verify(http, times(2)).postForEntity(eq(WEBHOOK), any(), eq(String.class));
    }

    @Test
    void concurrentCallsDeliverOnceWithinCooldown() throws Exception {
        RestTemplate http = mock(RestTemplate.class);
        when(http.postForEntity(eq(WEBHOOK), any(), eq(String.class)))
                .thenReturn(ResponseEntity.ok("ok"));
        AlertService alerts = new AlertService(http, clock, true, WEBHOOK, "");
        ExecutorService pool = Executors.newFixedThreadPool(8);
        CountDownLatch start = new CountDownLatch(1);
        try {
            List<Future<Boolean>> results = new ArrayList<>();
            for (int i = 0; i < 16; i++) {
                results.add(pool.submit(() -> { start.await(); return alerts.sendAlert("HIGH", "same", "message"); }));
            }
            start.countDown();
            long delivered = 0;
            for (Future<Boolean> result : results) if (result.get()) delivered++;
            assertTrue(delivered == 1);
            verify(http, times(1)).postForEntity(eq(WEBHOOK), any(), eq(String.class));
        } finally {
            pool.shutdownNow();
        }
    }
}
