package com.harshit.monitoring.repository;

import com.harshit.monitoring.entity.ApiRequestLog;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.Set;

/*
 Repository = DB access layer
*/
public interface ApiRequestLogRepository
        extends JpaRepository<ApiRequestLog, Long> {

    /*
     URI-wise aggregation:
     uri | count | avg latency
    */
    @Query("""
        SELECT l.uri, COUNT(l), AVG(l.responseTime)
        FROM ApiRequestLog l
        GROUP BY l.uri
    """)
    List<Object[]> getEndpointStats();

    /*
     Global average latency (excluding dashboard)
    */
    @Query("""
        SELECT AVG(l.responseTime)
        FROM ApiRequestLog l
        WHERE l.uri NOT LIKE '/monitor%'
    """)
    Double getGlobalAverageResponseTime();

    /*
     Error requests (>=400), excluding dashboard
    */
    @Query("""
        SELECT COUNT(l)
        FROM ApiRequestLog l
        WHERE l.status >= 400
          AND l.uri NOT LIKE '/monitor%'
    """)
    long countErrorRequests();

    /*
     Total non-dashboard requests
    */
    @Query("""
        SELECT COUNT(l)
        FROM ApiRequestLog l
        WHERE l.uri NOT LIKE '/monitor%'
    """)
    long countNonMonitorRequests();

    /*
     Distinct API URIs
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
    Double getAverageResponseTimeByUri(String uri);
}