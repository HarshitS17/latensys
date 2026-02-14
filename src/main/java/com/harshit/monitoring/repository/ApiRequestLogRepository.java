package com.harshit.monitoring.repository;

import com.harshit.monitoring.entity.ApiRequestLog;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;

/*
 Repository Layer
 ----------------
 Responsible for all database-level aggregation queries.
 We keep:
 - Global metrics (lifetime-based)
 - Window-aware metrics (time filtered)
*/
public interface ApiRequestLogRepository
        extends JpaRepository<ApiRequestLog, Long> {

    /*
     URI-wise aggregation (lifetime)
     Used earlier for non-windowed stats
    */
    @Query("""
        SELECT l.uri, COUNT(l), AVG(l.responseTime)
        FROM ApiRequestLog l
        GROUP BY l.uri
    """)
    List<Object[]> getEndpointStats();

    /*
     Global average latency (excluding dashboard endpoints)
    */
    @Query("""
        SELECT AVG(l.responseTime)
        FROM ApiRequestLog l
        WHERE l.uri NOT LIKE '/monitor%'
    """)
    Double getGlobalAverageResponseTime();

    /*
     Count all error requests (status >= 400)
     Used for global error rate calculation
    */
    @Query("""
        SELECT COUNT(l)
        FROM ApiRequestLog l
        WHERE l.status >= 400
          AND l.uri NOT LIKE '/monitor%'
    """)
    long countErrorRequests();

    /*
     Count all non-dashboard requests
     Used for global request count
    */
    @Query("""
        SELECT COUNT(l)
        FROM ApiRequestLog l
        WHERE l.uri NOT LIKE '/monitor%'
    """)
    long countNonMonitorRequests();

    /*
     Distinct URIs (lifetime-based)
    */
    @Query("""
        SELECT DISTINCT l.uri
        FROM ApiRequestLog l
        WHERE l.uri NOT LIKE '/monitor%'
    """)
    Set<String> findDistinctUris();

    long countByUri(String uri);

    @Query("""
        SELECT AVG(l.responseTime)
        FROM ApiRequestLog l
        WHERE l.uri = :uri
    """)
    Double getAverageResponseTimeByUri(@Param("uri") String uri);


    /* =======================================================
       WINDOW-AWARE METHODS (NEW ADDITION)
       These enable 5min / 15min filtering from DB
       ======================================================= */

    /*
     Get distinct URIs only within selected time window
     Example:
       SELECT DISTINCT uri
       WHERE timestamp >= now - 5min
    */
    @Query("""
        SELECT DISTINCT l.uri
        FROM ApiRequestLog l
        WHERE l.uri NOT LIKE '/monitor%'
          AND l.timestamp >= :cutoff
    """)
    Set<String> findDistinctUrisAfter(
            @Param("cutoff") LocalDateTime cutoff
    );

    /*
     Count requests per URI within window
    */
    @Query("""
        SELECT COUNT(l)
        FROM ApiRequestLog l
        WHERE l.uri = :uri
          AND l.timestamp >= :cutoff
    """)
    long countByUriAfter(
            @Param("uri") String uri,
            @Param("cutoff") LocalDateTime cutoff
    );

    /*
     Average latency per URI within window
    */
    @Query("""
        SELECT AVG(l.responseTime)
        FROM ApiRequestLog l
        WHERE l.uri = :uri
          AND l.timestamp >= :cutoff
    """)
    Double getAverageResponseTimeByUriAfter(
            @Param("uri") String uri,
            @Param("cutoff") LocalDateTime cutoff
    );
}