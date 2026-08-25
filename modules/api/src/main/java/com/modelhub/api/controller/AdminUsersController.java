package com.modelhub.api.controller;

import com.modelhub.api.support.Principals;
import com.modelhub.identity.security.CurrentPrincipal;
import com.modelhub.identity.service.AdminUserService;
import com.modelhub.identity.service.AdminUserService.AdminUserView;
import com.modelhub.identity.service.RbacAdminService;
import com.modelhub.shared.paging.CursorQuery;
import com.modelhub.shared.paging.CursorResult;
import com.modelhub.shared.web.ApiEnvelope;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * 管理端用户端点（管理后台计划 §五）：仅 platform_admin，服务层二次校验。
 * 列表为 cursor 分页；:disable/:enable 要求 Idempotency-Key 并回显（轻量模式）。
 */
@RestController
@RequestMapping("/api/v1/admin/users")
public class AdminUsersController {

    private final AdminUserService adminUsers;
    private final RbacAdminService rbac;

    public AdminUsersController(AdminUserService adminUsers, RbacAdminService rbac) {
        this.adminUsers = adminUsers;
        this.rbac = rbac;
    }

    /** 用户列表：q（username 模糊）+ status 过滤，cursor 分页。 */
    @GetMapping
    public ResponseEntity<ApiEnvelope<Map<String, Object>>> list(@RequestParam Map<String, String> params,
                                                                 HttpServletRequest request) {
        CurrentPrincipal actor = Principals.requireCurrent(request);
        CursorQuery cursor = CursorQuery.from(params);
        CursorResult<AdminUserView> result =
                adminUsers.listUsers(actor, cursor, params.get("q"), params.get("status"));
        return ResponseEntity.ok(ApiEnvelope.ok(Map.of(
                "items", result.items(),
                "nextCursor", result.nextCursor() == null ? "" : result.nextCursor())));
    }

    @PostMapping("/{userId:[0-9a-fA-F-]{36}}:disable")
    public ResponseEntity<ApiEnvelope<AdminUserView>> disable(@PathVariable("userId") UUID userId,
                                                              @RequestHeader("Idempotency-Key") String idempotencyKey,
                                                              HttpServletRequest request) {
        CurrentPrincipal actor = Principals.requireCurrent(request);
        return ResponseEntity.ok().header("Idempotency-Key", idempotencyKey)
                .body(ApiEnvelope.ok(adminUsers.disableUser(actor, userId)));
    }

    @PostMapping("/{userId:[0-9a-fA-F-]{36}}:enable")
    public ResponseEntity<ApiEnvelope<AdminUserView>> enable(@PathVariable("userId") UUID userId,
                                                             @RequestHeader("Idempotency-Key") String idempotencyKey,
                                                             HttpServletRequest request) {
        CurrentPrincipal actor = Principals.requireCurrent(request);
        return ResponseEntity.ok().header("Idempotency-Key", idempotencyKey)
                .body(ApiEnvelope.ok(adminUsers.enableUser(actor, userId)));
    }

    /** 指定用户的活跃平台角色列表。 */
    @GetMapping("/{userId:[0-9a-fA-F-]{36}}/roles")
    public ResponseEntity<ApiEnvelope<Map<String, Object>>> userRoles(@PathVariable("userId") UUID userId,
                                                                      HttpServletRequest request) {
        CurrentPrincipal actor = Principals.requireCurrent(request);
        List<String> roles = rbac.activeRoleCodes(actor, userId);
        return ResponseEntity.ok(ApiEnvelope.ok(Map.of("roles", roles)));
    }
}
