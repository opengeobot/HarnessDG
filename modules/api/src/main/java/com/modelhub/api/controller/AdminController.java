package com.modelhub.api.controller;

import com.modelhub.api.support.Principals;
import com.modelhub.identity.service.AuthService;
import com.modelhub.shared.web.ApiEnvelope;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;
import java.util.UUID;

/**
 * 管理端点（04 §4.1）：目前包含账户解锁；后续阶段扩展审计查询/配额等。
 * 仅 platform_admin 可访问，服务层二次校验。
 */
@RestController
@RequestMapping("/api/v1/admin")
public class AdminController {

    private final AuthService authService;

    public AdminController(AuthService authService) {
        this.authService = authService;
    }

    @PostMapping("/users/{userId}:unlock")
    public ResponseEntity<ApiEnvelope<Map<String, String>>> unlock(@PathVariable("userId") UUID userId,
                                                                   jakarta.servlet.http.HttpServletRequest request) {
        authService.unlockUser(Principals.requireCurrent(request), userId);
        return ResponseEntity.ok(ApiEnvelope.ok(Map.of("userId", userId.toString(), "status", "active")));
    }
}
