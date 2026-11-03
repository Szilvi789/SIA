package com.personalai.platform.common;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class HealthController {
    @GetMapping("/api/health")
    public HealthResponse health() {
        return new HealthResponse("UP", "mock");
    }

    public record HealthResponse(String status, String provider) { }
}
