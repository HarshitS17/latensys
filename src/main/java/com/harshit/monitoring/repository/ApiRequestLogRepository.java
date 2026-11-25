package com.harshit.monitoring.repository;

import com.harshit.monitoring.entity.ApiRequestLog;
import java.time.LocalDateTime;
import java.util.Set;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ApiRequestLogRepository extends JpaRepository<ApiRequestLog, Long> {
    @Query("SELECT COUNT(l) FROM ApiRequestLog l")
    long countAttempts();

    @Query("SELECT AVG(l.responseTime) FROM ApiRequestLog l WHERE l.decision = 'ALLOWED' OR (l.decision IS NULL AND l.status <> 429)")
    Double getGlobalAverageResponseTime();

    @Query("SELECT DISTINCT l.uri FROM ApiRequestLog l WHERE l.timestamp >= :cutoff AND (l.decision = 'ALLOWED' OR (l.decision IS NULL AND l.status <> 429))")
    Set<String> findDistinctUrisAfter(@Param("cutoff") LocalDateTime cutoff);

    @Query("SELECT COUNT(l) FROM ApiRequestLog l WHERE l.uri = :uri AND l.timestamp >= :cutoff AND (l.decision = 'ALLOWED' OR (l.decision IS NULL AND l.status <> 429))")
    long countByUriAfter(@Param("uri") String uri, @Param("cutoff") LocalDateTime cutoff);

    @Query("SELECT AVG(l.responseTime) FROM ApiRequestLog l WHERE l.uri = :uri AND l.timestamp >= :cutoff AND (l.decision = 'ALLOWED' OR (l.decision IS NULL AND l.status <> 429))")
    Double getAverageResponseTimeByUriAfter(@Param("uri") String uri,
                                            @Param("cutoff") LocalDateTime cutoff);

    @Query("SELECT COUNT(l) FROM ApiRequestLog l WHERE l.decision = 'BLOCKED' OR (l.decision IS NULL AND l.status = 429)")
    long countBlockedRequests();

    @Query("SELECT COUNT(l) FROM ApiRequestLog l WHERE l.decision = 'UNAVAILABLE'")
    long countUnavailableRequests();

    @Query("SELECT COUNT(l) FROM ApiRequestLog l WHERE l.decision = 'ALLOWED' OR (l.decision IS NULL AND l.status <> 429)")
    long countAllowedRequests();

    @Query("SELECT COUNT(l) FROM ApiRequestLog l WHERE l.status < 400 AND (l.decision = 'ALLOWED' OR (l.decision IS NULL AND l.status <> 429))")
    long countSuccessfulRequests();

    @Query("SELECT COUNT(l) FROM ApiRequestLog l WHERE l.status BETWEEN 400 AND 499 AND (l.decision = 'ALLOWED' OR (l.decision IS NULL AND l.status <> 429))")
    long countClientErrors();

    @Query("SELECT COUNT(l) FROM ApiRequestLog l WHERE l.status >= 500 AND (l.decision = 'ALLOWED' OR (l.decision IS NULL AND l.status <> 429))")
    long countServerErrors();

    @Query("SELECT COUNT(l) FROM ApiRequestLog l WHERE l.timestamp >= :start AND l.timestamp < :end")
    long countAttemptsBetween(@Param("start") LocalDateTime start, @Param("end") LocalDateTime end);

    @Query("SELECT COUNT(l) FROM ApiRequestLog l WHERE l.timestamp >= :start AND l.timestamp < :end AND (l.decision = 'BLOCKED' OR (l.decision IS NULL AND l.status = 429))")
    long countBlockedBetween(@Param("start") LocalDateTime start, @Param("end") LocalDateTime end);
}
