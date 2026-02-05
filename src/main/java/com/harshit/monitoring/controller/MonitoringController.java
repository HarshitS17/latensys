package com.harshit.monitoring.controller;

import com.harshit.monitoring.dto.EndpointStatsDTO;
import com.harshit.monitoring.service.MonitoringService;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

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
        return Map.of(
                "totalRequests", service.getTotalRequests(),
                "averageResponseTime", service.getAverageResponseTime()
        );
    }

    @GetMapping("/monitor/errors")
    public Map<String, Object> getErrorRate() {
        return Map.of(
                "errorRate", service.getErrorRate()
        );
    }

    /*
     window = 5 or 15
    */
    @GetMapping("/monitor/endpoints")
    public List<EndpointStatsDTO> getEndpointStats(
            @RequestParam(defaultValue = "5") int window
    ) {
        return service.getEndpointStats(window);
    }
}