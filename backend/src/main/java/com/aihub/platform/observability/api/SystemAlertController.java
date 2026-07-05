/*
 * 功能: 系统告警 REST 适配器——查询告警历史与活跃告警。
 * 时间: 2026-07-06
 * 作者: AxeXie
 */
package com.aihub.platform.observability.api;

import com.aihub.authorization.application.AuthorizationService;
import com.aihub.authorization.domain.Permissions;
import com.aihub.platform.observability.application.AlertService;
import com.aihub.platform.observability.domain.SystemAlert;
import com.aihub.shared.api.ApiResponse;
import com.aihub.shared.identity.PrincipalContext;
import com.aihub.shared.identity.PrincipalContextHolder;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 系统告警 REST 适配器。
 *
 * <p>要求 {@code system:observe} 权限（fail-closed）。
 */
@RestController
@RequestMapping("/api/v1/system/alerts")
public class SystemAlertController {

    private final AlertService alertService;
    private final AuthorizationService authorizationService;

    public SystemAlertController(AlertService alertService,
                                  AuthorizationService authorizationService) {
        this.alertService = alertService;
        this.authorizationService = authorizationService;
    }

    /** 查询告警列表（按触发时间降序）。 */
    @GetMapping
    public ApiResponse<List<SystemAlert>> listAlerts(
            @RequestParam(required = false, defaultValue = "50") int limit) {
        authorizationService.requirePermission(Permissions.SYSTEM_OBSERVE);
        return respond(alertService.listAlerts(limit));
    }

    /** 查询当前活跃告警（FIRING）。 */
    @GetMapping("/firing")
    public ApiResponse<List<SystemAlert>> listFiringAlerts() {
        authorizationService.requirePermission(Permissions.SYSTEM_OBSERVE);
        return respond(alertService.listFiringAlerts());
    }

    private static <T> ApiResponse<T> respond(T data) {
        String requestId = PrincipalContextHolder.current().map(PrincipalContext::requestId).orElse(null);
        String traceId = PrincipalContextHolder.current().map(PrincipalContext::traceId).orElse(null);
        return ApiResponse.of(data, requestId, traceId);
    }
}
