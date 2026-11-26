package com.harshit.monitoring.repository;

import com.harshit.monitoring.entity.ApiRequestLog;
import java.time.LocalDateTime;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

public interface ApiRequestLogRepository extends JpaRepository<ApiRequestLog, Long> {
    String ACCOUNTING = """
        SELECT COUNT(*),
          COUNT(*) FILTER (WHERE decision = 'ALLOWED' OR (decision IS NULL AND status <> 429)),
          COUNT(*) FILTER (WHERE decision = 'BLOCKED' OR (decision IS NULL AND status = 429)),
          COUNT(*) FILTER (WHERE decision = 'UNAVAILABLE'),
          COUNT(*) FILTER (WHERE status < 400 AND (decision = 'ALLOWED' OR (decision IS NULL AND status <> 429))),
          COUNT(*) FILTER (WHERE status BETWEEN 400 AND 499 AND (decision = 'ALLOWED' OR (decision IS NULL AND status <> 429))),
          COUNT(*) FILTER (WHERE status >= 500 AND (decision = 'ALLOWED' OR (decision IS NULL AND status <> 429))),
          AVG(response_time) FILTER (WHERE decision = 'ALLOWED' OR (decision IS NULL AND status <> 429))
        FROM api_request_logs
        """;

    @Query(value = ACCOUNTING + " WHERE timestamp >= :cutoff", nativeQuery = true)
    List<Object[]> aggregateRetained(@Param("cutoff") LocalDateTime cutoff);

    @Query(value = ACCOUNTING + " WHERE timestamp >= :start AND timestamp < :end", nativeQuery = true)
    List<Object[]> aggregateBetween(@Param("start") LocalDateTime start, @Param("end") LocalDateTime end);

    @Query("""
        SELECT l.uri, COUNT(l), AVG(l.responseTime)
        FROM ApiRequestLog l
        WHERE l.timestamp >= :cutoff
          AND (l.decision = 'ALLOWED' OR (l.decision IS NULL AND l.status <> 429))
        GROUP BY l.uri
        """)
    List<Object[]> aggregateEndpointsAfter(@Param("cutoff") LocalDateTime cutoff);

    @Query(value = """
        SELECT date_trunc('minute', timestamp), COUNT(*),
          COUNT(*) FILTER (WHERE decision = 'BLOCKED' OR (decision IS NULL AND status = 429))
        FROM api_request_logs
        WHERE timestamp >= :start AND timestamp < :end
        GROUP BY date_trunc('minute', timestamp)
        ORDER BY 1
        """, nativeQuery = true)
    List<Object[]> aggregateMinutes(@Param("start") LocalDateTime start,
                                    @Param("end") LocalDateTime end);

    @Modifying
    @Transactional
    @Query(value = """
        DELETE FROM api_request_logs WHERE id IN (
          SELECT id FROM api_request_logs
          WHERE timestamp < :cutoff
          ORDER BY timestamp, id
          LIMIT :batchSize
        )
        """, nativeQuery = true)
    int deleteExpiredBatch(@Param("cutoff") LocalDateTime cutoff,
                           @Param("batchSize") int batchSize);
}
