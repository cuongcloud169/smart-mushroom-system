package com.mushroom.controller;

import java.time.OffsetDateTime;
import java.util.Map;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/** Kiểm tra server còn chạy: GET /api/health */
@RestController
public class HealthController {
    @GetMapping("/api/health")
    public Map<String, Object> health() {
        return Map.of("ok", true, "time", OffsetDateTime.now().toString());
    }
}
