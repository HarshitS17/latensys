package com.harshit.monitoring.filter;

import com.harshit.monitoring.config.RateLimitConfig;
import com.harshit.monitoring.entity.ApiRequestLog;
import com.harshit.monitoring.repository.ApiRequestLogRepository;
import com.harshit.monitoring.service.LatencySampleStore;
import com.harshit.monitoring.service.RedisRateLimiter;
import jakarta.servlet.DispatcherType;
import jakarta.servlet.FilterChain;
import java.io.IOException;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class FilterReliabilityTest {
    private final ApiRequestLogRepository repository = mock(ApiRequestLogRepository.class);
    private final LatencySampleStore samples = mock(LatencySampleStore.class);
    private final MonitoringFilter monitoring = new MonitoringFilter(repository, samples);

    @Test
    void databaseFailureDoesNotChangeSuccessfulResponse() throws Exception {
        when(repository.save(any(ApiRequestLog.class))).thenThrow(new IllegalStateException("database down"));
        MockHttpServletResponse response = new MockHttpServletResponse();
        monitoring.doFilter(request("/api/demo"), response, (req, res) -> res.getWriter().write("ok"));
        assertEquals(200, response.getStatus());
        assertEquals("ok", response.getContentAsString());
        verify(samples).record(eq("/api/demo"), anyLong());
    }

    @Test
    void redisMonitoringFailureDoesNotChangeSuccessfulResponse() throws Exception {
        org.mockito.Mockito.doThrow(new IllegalStateException("redis down"))
                .when(samples).record(eq("/api/demo"), anyLong());
        MockHttpServletResponse response = new MockHttpServletResponse();
        monitoring.doFilter(request("/api/demo"), response, (req, res) -> res.getWriter().write("ok"));
        assertEquals(200, response.getStatus());
        assertEquals("ok", response.getContentAsString());
        verify(repository).save(any(ApiRequestLog.class));
    }

    @Test
    void thrownHandlerIsRecordedAsServerErrorOnce() throws Exception {
        MockHttpServletRequest request = request("/api/demo");
        assertThrows(IOException.class, () -> monitoring.doFilter(request, new MockHttpServletResponse(),
                (req, res) -> { throw new IOException("handler failure"); }));
        ArgumentCaptor<ApiRequestLog> saved = ArgumentCaptor.forClass(ApiRequestLog.class);
        verify(repository).save(saved.capture());
        assertEquals(500, saved.getValue().getStatus());
        assertEquals("ALLOWED", saved.getValue().getDecision());
        verify(samples).record(eq("/api/demo"), anyLong());
    }

    @Test
    void rejectedAndUnavailableRequestsAreRecordedOnce() throws Exception {
        RedisRateLimiter limiter = mock(RedisRateLimiter.class);
        RateLimitingFilter rateFilter = new RateLimitingFilter(limiter, new RateLimitConfig());
        when(limiter.check(any(), eq("/api/demo"), eq(50), eq(60)))
                .thenReturn(new RedisRateLimiter.Decision(false, 0, 1500));
        MockHttpServletResponse rejected = new MockHttpServletResponse();
        FilterChain inner = (req, res) -> rateFilter.doFilter(req, res,
                (nextReq, nextRes) -> { throw new AssertionError("should be rejected"); });
        monitoring.doFilter(request("/api/demo"), rejected, inner);
        assertEquals(429, rejected.getStatus());
        assertEquals("2", rejected.getHeader("Retry-After"));
        assertTrue(rejected.getContentAsString().contains("rate_limited"));
        ArgumentCaptor<ApiRequestLog> saved = ArgumentCaptor.forClass(ApiRequestLog.class);
        verify(repository).save(saved.capture());
        assertEquals("BLOCKED", saved.getValue().getDecision());
        verify(samples, never()).record(any(), anyLong());

        ApiRequestLogRepository otherRepository = mock(ApiRequestLogRepository.class);
        MonitoringFilter otherMonitoring = new MonitoringFilter(otherRepository, samples);
        when(limiter.check(any(), eq("/api/unavailable"), eq(50), eq(60)))
                .thenThrow(new IllegalStateException("redis down"));
        MockHttpServletResponse unavailable = new MockHttpServletResponse();
        otherMonitoring.doFilter(request("/api/unavailable"), unavailable,
                (req, res) -> rateFilter.doFilter(req, res, (nextReq, nextRes) -> {}));
        assertEquals(503, unavailable.getStatus());
        verify(otherRepository).save(saved.capture());
        assertEquals("UNAVAILABLE", saved.getValue().getDecision());
    }

    @Test
    void internalAndErrorDispatchesAreNotRecorded() throws Exception {
        monitoring.doFilter(request("/monitor/metrics"), new MockHttpServletResponse(),
                (req, res) -> {});
        MockHttpServletRequest error = request("/api/demo");
        error.setDispatcherType(DispatcherType.ERROR);
        monitoring.doFilter(error, new MockHttpServletResponse(), (req, res) -> {});
        verify(repository, never()).save(any(ApiRequestLog.class));
        verify(samples, never()).record(any(), anyLong());
    }

    private MockHttpServletRequest request(String uri) {
        return new MockHttpServletRequest("GET", uri);
    }
}
