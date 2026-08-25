package com.modelhub.api.controller;

import com.modelhub.api.support.Principals;
import com.modelhub.catalog.service.SystemOverviewService;
import com.modelhub.identity.security.CurrentPrincipal;
import com.modelhub.identity.service.AuditQueryService;
import com.modelhub.identity.service.AuditQueryService.AuditLogView;
import com.modelhub.identity.service.AuthService;
import com.modelhub.shared.error.ApiException;
import com.modelhub.shared.paging.CursorQuery;
import com.modelhub.shared.paging.CursorResult;
import com.modelhub.shared.web.ApiEnvelope;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody;

import java.time.OffsetDateTime;
import java.time.format.DateTimeParseException;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * 管理端点（04 §4.1）：账户解锁 + 审计查询/导出（SEC-05）。
 * 仅 platform_admin（写）与 platform_admin/platform_auditor（审计读）可访问，服务层二次校验。
 */
@RestController
@RequestMapping("/api/v1/admin")
public class AdminController {

    private final AuthService authService;
    private final AuditQueryService auditQuery;
    private final SystemOverviewService systemOverview;

    public AdminController(AuthService authService, AuditQueryService auditQuery,
                           SystemOverviewService systemOverview) {
        this.authService = authService;
        this.auditQuery = auditQuery;
        this.systemOverview = systemOverview;
    }

    @PostMapping("/users/{userId:[0-9a-fA-F-]{36}}:unlock")
    public ResponseEntity<Void> unlock(@PathVariable("userId") UUID userId,
                                  @RequestHeader("Idempotency-Key") String idempotencyKey,
                                                                   HttpServletRequest request) {
        authService.unlockUser(Principals.requireCurrent(request), userId);
        return ResponseEntity.noContent().header("Idempotency-Key", idempotencyKey).build();
    }

    /** 审计查询（04 §4.1）：format=json（默认）返回 cursor 分页信封。 */
    @GetMapping("/audit-logs")
    public ResponseEntity<ApiEnvelope<Map<String, Object>>> auditLogs(@RequestParam Map<String, String> params,
                                                                      HttpServletRequest request) {
        CurrentPrincipal actor = Principals.requireCurrent(request);
        String format = params.getOrDefault("format", "json");
        if (!"json".equals(format)) {
            throw ApiException.badRequest("format 仅支持 json 或 ndjson",
                    List.of(new ApiException.Detail("format", "invalid_value")));
        }
        CursorQuery cursor = CursorQuery.from(params);
        CursorResult<AuditLogView> result =
                auditQuery.query(actor, cursor, params.get("actor"), params.get("action"),
                        params.get("resource"), parseTime(params.get("from"), "from"),
                        parseTime(params.get("to"), "to"));
        return ResponseEntity.ok(ApiEnvelope.ok(Map.of(
                "items", result.items(),
                "nextCursor", result.nextCursor() == null ? "" : result.nextCursor())));
    }

    /**
     * 审计导出（04 §4.1）：format=ndjson 流式导出。
     * 返回类型必须声明为 ResponseEntity&lt;StreamingResponseBody&gt;：
     * StreamingResponseBodyReturnValueHandler 按声明类型选择处理器，声明为通配类型会落到消息转换器导致 500。
     */
    @GetMapping(path = "/audit-logs", params = "format=ndjson")
    public ResponseEntity<StreamingResponseBody> auditLogsNdjson(@RequestParam Map<String, String> params,
                                                                 HttpServletRequest request) {
        CurrentPrincipal actor = Principals.requireCurrent(request);
        StreamingResponseBody body = out ->
                auditQuery.exportNdjson(actor, params.get("actor"), params.get("action"),
                        params.get("resource"), parseTime(params.get("from"), "from"),
                        parseTime(params.get("to"), "to"), out);
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType("application/x-ndjson"))
                .body(body);
    }

    /** 系统概览（管理后台 §五）：平台计数 + 健康检查，admin/auditor 可读。 */
    @GetMapping("/system/overview")
    public ResponseEntity<ApiEnvelope<SystemOverviewService.OverviewView>> systemOverview(
            HttpServletRequest request) {
        CurrentPrincipal actor = Principals.requireCurrent(request);
        return ResponseEntity.ok(ApiEnvelope.ok(systemOverview.overview(actor)));
    }

    /** from/to 参数解析：RFC-3339（如 2026-08-25T00:00:00Z），缺失返回 null。 */
    private static OffsetDateTime parseTime(String raw, String name) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        try {
            return OffsetDateTime.parse(raw.trim());
        } catch (DateTimeParseException e) {
            throw ApiException.badRequest(name + " 必须为 RFC-3339 时间（如 2026-08-25T00:00:00Z）",
                    List.of(new ApiException.Detail(name, "invalid_format")));
        }
    }
}
