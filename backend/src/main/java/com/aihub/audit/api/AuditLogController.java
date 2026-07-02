/*
 * 功能: 审计查询 REST 适配器——游标查询不可变审计记录；授权走统一 AuthorizationService。
 * 时间: 2026-07-01
 * 作者: AxeXie
 */
package com.aihub.audit.api;

import com.aihub.audit.application.AuditLogView;
import com.aihub.audit.application.AuditQueryService;
import com.aihub.authorization.application.AuthorizationService;
import com.aihub.authorization.domain.Permissions;
import com.aihub.shared.api.ApiResponse;
import com.aihub.shared.api.CursorPage;
import com.aihub.shared.identity.PrincipalContext;
import com.aihub.shared.identity.PrincipalContextHolder;
import java.time.Instant;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 审计查询 REST 适配器。
 *
 * <p>仅提供查询端点（GET），无 UPDATE/DELETE 端点，保证审计不可篡改。
 * 列表需 {@code audit:read}（统一授权 fail-closed）。适配层不含业务规则。
 */
@RestController
@RequestMapping("/api/v1/system/audit-logs")
public class AuditLogController {

    private final AuditQueryService auditQueryService;
    private final AuthorizationService authorizationService;

    public AuditLogController(AuditQueryService auditQueryService, AuthorizationService authorizationService) {
        this.auditQueryService = auditQueryService;
        this.authorizationService = authorizationService;
    }

    /**
     * 游标查询不可变审计记录。
     */
    @GetMapping
    public ApiResponse<CursorPage<AuditLogView>> listAuditLogs(
            @RequestParam(required = false) String eventType,
            @RequestParam(required = false) String principalId,
            @RequestParam(required = false) String action,
            @RequestParam(required = false) String resourceType,
            @RequestParam(required = false) String resourceId,
            @RequestParam(required = false) Instant createdAfter,
            @RequestParam(required = false) Instant createdBefore,
            @RequestParam(required = false) String cursor,
            @RequestParam(required = false, defaultValue = "20") int limit) {
        authorizationService.requirePermission(Permissions.AUDIT_READ);
        int effectiveLimit = Math.min(Math.max(limit, 1), 100);
        CursorPage<AuditLogView> page;
        if (eventType != null || resourceType != null || createdAfter != null || createdBefore != null) {
            page = auditQueryService.searchAuditLogs(eventType, principalId, resourceType, resourceId,
                    createdAfter, createdBefore, cursor, effectiveLimit);
        } else {
            page = auditQueryService.listAuditLogs(principalId, action, resourceId, cursor, effectiveLimit);
        }
        return respond(page);
    }

    private static <T> ApiResponse<T> respond(T data) {
        String requestId = PrincipalContextHolder.current().map(PrincipalContext::requestId).orElse(null);
        String traceId = PrincipalContextHolder.current().map(PrincipalContext::traceId).orElse(null);
        return ApiResponse.of(data, requestId, traceId);
    }
}
