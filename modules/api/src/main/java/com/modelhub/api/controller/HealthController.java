package com.modelhub.api.controller;

import com.modelhub.identity.service.SystemHealthService;
import com.modelhub.identity.service.SystemHealthService.HealthStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 健康端点（04/07 §5.3）：最小 JSON，不使用 envelope，不暴露凭据与内部地址。
 */
@RestController
public class HealthController {

    private final SystemHealthService healthService;

    public HealthController(SystemHealthService healthService) {
        this.healthService = healthService;
    }

    @GetMapping("/api/v1/livez")
    public HealthStatus livez() {
        return healthService.live();
    }

    @GetMapping("/api/v1/readyz")
    public ResponseEntity<HealthStatus> readyz() {
        HealthStatus status = healthService.ready();
        return "ok".equals(status.status())
                ? ResponseEntity.ok(status)
                : ResponseEntity.status(503).body(status);
    }
}
