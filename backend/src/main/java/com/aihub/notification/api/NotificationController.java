/*
 * 功能: 通知管理 REST 适配器——查询当前主体通知与标记已读；授权走统一 AuthorizationService。
 * 时间: 2026-07-01
 * 作者: AxeXie
 */
package com.aihub.notification.api;

import com.aihub.authorization.application.AuthorizationService;
import com.aihub.authorization.domain.Permissions;
import com.aihub.notification.application.NotificationService;
import com.aihub.notification.application.NotificationView;
import com.aihub.shared.api.ApiResponse;
import com.aihub.shared.api.CursorPage;
import com.aihub.shared.identity.PrincipalContext;
import com.aihub.shared.identity.PrincipalContextHolder;
import java.util.Map;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 通知管理 REST 适配器。
 *
 * <p>列表与标记已读均需 {@code notification:read}（统一授权 fail-closed）。
 * 适配层不含业务规则，编排委托 {@link NotificationService}。
 */
@RestController
@RequestMapping("/api/v1/system/notifications")
public class NotificationController {

    private final NotificationService notificationService;
    private final AuthorizationService authorizationService;

    public NotificationController(NotificationService notificationService,
                                  AuthorizationService authorizationService) {
        this.notificationService = notificationService;
        this.authorizationService = authorizationService;
    }

    /**
     * 查询当前主体站内通知。
     */
    @GetMapping
    public ApiResponse<CursorPage<NotificationView>> listNotifications(
            @RequestParam(required = false, defaultValue = "false") boolean unreadOnly,
            @RequestParam(required = false) String cursor,
            @RequestParam(required = false, defaultValue = "20") int limit) {
        authorizationService.requirePermission(Permissions.NOTIFICATION_READ);
        int effectiveLimit = Math.min(Math.max(limit, 1), 100);
        return respond(notificationService.listNotifications(unreadOnly, cursor, effectiveLimit));
    }

    /**
     * 标记当前主体通知已读。
     */
    @PostMapping("/{notificationId}:read")
    public ApiResponse<Map<String, String>> markRead(@PathVariable String notificationId) {
        authorizationService.requirePermission(Permissions.NOTIFICATION_READ);
        notificationService.markRead(notificationId);
        return respond(Map.of("notificationId", notificationId, "status", "READ"));
    }

    private static <T> ApiResponse<T> respond(T data) {
        String requestId = PrincipalContextHolder.current().map(PrincipalContext::requestId).orElse(null);
        String traceId = PrincipalContextHolder.current().map(PrincipalContext::traceId).orElse(null);
        return ApiResponse.of(data, requestId, traceId);
    }
}
