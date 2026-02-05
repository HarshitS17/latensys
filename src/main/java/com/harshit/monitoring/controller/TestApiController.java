package com.harshit.monitoring.controller;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.data.redis.core.StringRedisTemplate;

/*
 This controller exists ONLY to generate API traffic.
 Every call here will be logged by the filter.
*/
@RestController
public class TestApiController {

    // Redis template injected by Spring
    private final StringRedisTemplate redis;

    // Constructor injection (recommended)
    public TestApiController(StringRedisTemplate redis) {
        this.redis = redis;
    }

    @GetMapping("/test/hello")
    public String hello() {
        return "Hello API Monitoring";
    }

    @GetMapping("/redis/test")
    public String testRedis() {
        redis.opsForValue().increment("test:key");
        return redis.opsForValue().get("test:key");
    }
}
