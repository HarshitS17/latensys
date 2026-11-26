package com.harshit.demo;

import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class DemoController {
    private final AtomicInteger helloCalls = new AtomicInteger();
    private final AtomicInteger failureCalls = new AtomicInteger();

    @GetMapping("/hello")
    public Map<String, Object> hello() {
        return Map.of("service", "independent-demo", "message", "Hello from the upstream app",
                "calls", helloCalls.incrementAndGet());
    }

    @GetMapping("/fail")
    public ResponseEntity<Map<String, String>> fail() {
        failureCalls.incrementAndGet();
        return ResponseEntity.status(500).body(Map.of("error", "demo_failure"));
    }

    @PostMapping(value = "/echo", consumes = MediaType.TEXT_PLAIN_VALUE)
    public ResponseEntity<String> echo(@RequestBody String body) {
        return ResponseEntity.ok().contentType(MediaType.TEXT_PLAIN).body(body);
    }

    @GetMapping("/stats")
    public Map<String, Integer> stats() {
        return Map.of("helloCalls", helloCalls.get(), "failureCalls", failureCalls.get());
    }

    @GetMapping("/slow")
    public Map<String, String> slow() throws InterruptedException {
        Thread.sleep(6000);
        return Map.of("status", "late");
    }
}
