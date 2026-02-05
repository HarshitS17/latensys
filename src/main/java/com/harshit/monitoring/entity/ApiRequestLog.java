package com.harshit.monitoring.entity;

import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "api_request_logs")
public class ApiRequestLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String method;
    private String uri;
    private int status;
    private long responseTime;
    private LocalDateTime timestamp;

    protected ApiRequestLog() {}

    public ApiRequestLog(String method, String uri, int status, long responseTime) {
        this.method = method;
        this.uri = uri;
        this.status = status;
        this.responseTime = responseTime;
        this.timestamp = LocalDateTime.now();
    }

    public String getUri() { return uri; }
    public int getStatus() { return status; }
    public long getResponseTime() { return responseTime; }
}