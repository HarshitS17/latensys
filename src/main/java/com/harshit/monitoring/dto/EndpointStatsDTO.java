package com.harshit.monitoring.dto;

/*
 DTO used to send endpoint-level metrics to dashboard.
*/
public class EndpointStatsDTO {

    private String uri;
    private long totalRequests;
    private double averageResponseTime;
    private Double p95Latency;

    // Constructor used by MonitoringService
    public EndpointStatsDTO(
            String uri,
            long totalRequests,
            double averageResponseTime,
            Double p95Latency
    ) {
        this.uri = uri;
        this.totalRequests = totalRequests;
        this.averageResponseTime = averageResponseTime;
        this.p95Latency = p95Latency;
    }

    public String getUri() {
        return uri;
    }

    public long getTotalRequests() {
        return totalRequests;
    }

    public double getAverageResponseTime() {
        return averageResponseTime;
    }

    public Double getP95Latency() {
        return p95Latency;
    }
}
