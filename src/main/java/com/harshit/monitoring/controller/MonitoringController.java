package com.harshit.monitoring.controller;

import com.harshit.monitoring.dto.EndpointStatsDTO;
import com.harshit.monitoring.service.MonitoringService;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Map;

/*
 Monitoring APIs consumed by dashboard
*/
@RestController
public class MonitoringController {

    private final MonitoringService service;

    public MonitoringController(MonitoringService service) {
        this.service = service;
    }

    @GetMapping("/monitor/metrics")
    public Map<String, Object> getMetrics() {
        MonitoringService.Accounting counts = service.getAccounting();
        return Map.of(
                "totalRequests", counts.attempted(),
                "attemptedRequests", counts.attempted(),
                "allowedRequests", counts.allowed(),
                "blockedRequests", counts.blocked(),
                "unavailableRequests", counts.unavailable(),
                "successfulRequests", counts.successful(),
                "clientErrors", counts.clientErrors(),
                "serverErrors", counts.serverErrors(),
                "averageResponseTime", counts.averageResponseTime(),
                "errorRate", counts.errorRate()
        );
    }

    @GetMapping("/monitor/errors")
    public Map<String, Object> getErrorRate() {
        MonitoringService.Accounting counts = service.getAccounting();
        return Map.of(
                "errorRate", counts.errorRate(),
                "clientErrors", counts.clientErrors(),
                "serverErrors", counts.serverErrors(),
                "allowedRequests", counts.allowed()
        );
    }

    /*
     window = 5 or 15
    */
    @GetMapping("/monitor/endpoints")
    public List<EndpointStatsDTO> getEndpointStats(
            @RequestParam(defaultValue = "5") int window
    ) {
        if (window != 5 && window != 15) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Supported windows are 5 and 15 minutes");
        }
        return service.getEndpointStats(window);
    }

    @GetMapping("/monitor/rpm")
    public List<Long> getRPMTrend() {
        return service.getLast15MinuteRPM();    }

    @GetMapping("/monitor/blocked-trend")
    public List<Long> getBlockedTrend() {
        return service.getLast15MinuteBlocked();
    }


}
