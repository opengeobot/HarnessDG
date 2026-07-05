/*
 * 功能: 通知管理 REST 适配器——管理员视角查询 Outbox/Webhook 投递与手动重试。
 * 时间: 2026-07-06
 * 作者: AxeXie
 */
package com.aihub.notification.api;

import com.aihub.authorization.application.AuthorizationService;
import com.aihub.authorization.domain.Permissions;
import com.aihub.notification.application.NotificationAdminService;
import com.aihub.notification.application.NotificationAdminService.OutboxEventView;
import com.aihub.notification.application.NotificationAdminService.WebhookDeliveryView;
import com.aihub.shared.api.ApiResponse;
import com.aihub.shared.identity.PrincipalContext;
import com.aihub.shared.identity.PrincipalContextHolder;
import java.util.List;
import java.util.Map;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 通知管理 REST 适配器（管理员视角）。
 *
 * <p>要求 {@code system:observe} 权限（fail-closed）。
 */
@RestController
@RequestMapping("/api/v1/system/notifications/admin")
public class NotificationAdminController {

    private final NotificationAdminService adminService;
    private final AuthorizationService authorizationService;

    public NotificationAdminController(NotificationAdminService adminService,
                                        AuthorizationService authorizationService) {
        this.adminService = adminService;
        this.authorizationService = authorizationService;
    }

    /** 查询最近 Outbox 事件。 */
    @GetMapping("/outbox")
    public ApiResponse<List<OutboxEventView>> listOutboxEvents(
            @RequestParam(required = false, defaultValue = "50") int limit) {
        authorizationService.requirePermission(Permissions.SYSTEM_OBSERVE);
        return respond(adminService.listOutboxEvents(limit));
    }

    /** 统计 Outbox 待处理事件数。 */
    @GetMapping("/outbox/pending-count")
    public ApiResponse<Map<String, Long>> countPendingOutbox() {
        authorizationService.requirePermission(Permissions.SYSTEM_OBSERVE);
        return respond(Map.of("count", adminService.countPendingOutbox()));
    }

    /** 查询最近 Webhook 投递记录。 */
    @GetMapping("/deliveries")
    public ApiResponse<List<WebhookDeliveryView>> listDeliveries(
            @RequestParam(required = false, defaultValue = "50") int limit) {
        authorizationService.requirePermission(Permissions.SYSTEM_OBSERVE);
        return respond(adminService.listDeliveries(limit));
    }

    /** 手动重试失败投递。 */
    @PostMapping("/deliveries/{deliveryId}:retry")
    public ApiResponse<Map<String, String>> retryDelivery(@PathVariable String deliveryId) {
        authorizationService.requirePermission(Permissions.SYSTEM_OBSERVE);
        adminService.retryDelivery(deliveryId);
        return respond(Map.of("deliveryId", deliveryId, "status", "PENDING"));
    }

    private static <T> ApiResponse<T> respond(T data) {
        String requestId = PrincipalContextHolder.current().map(PrincipalContext::requestId).orElse(null);
        String traceId = PrincipalContextHolder.current().map(PrincipalContext::traceId).orElse(null);
        return ApiResponse.of(data, requestId, traceId);
    }
}
