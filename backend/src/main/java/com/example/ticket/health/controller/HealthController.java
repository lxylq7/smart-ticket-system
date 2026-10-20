package com.example.ticket.health.controller;

import com.example.ticket.common.response.ApiResponse;
import com.example.ticket.health.service.HealthService;
import java.util.Map;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/health")
public class HealthController {

    private final HealthService healthService;

    public HealthController(HealthService healthService) {
        this.healthService = healthService;
    }

    @GetMapping
    public ApiResponse<Map<String, String>> health() {
        return ApiResponse.success(Map.of("status", "UP"));
    }

    @GetMapping("/db")
    public ApiResponse<Map<String, String>> databaseHealth() {
        healthService.checkDatabase();
        return ApiResponse.success(Map.of("status", "UP", "database", "UP"));
    }
}
