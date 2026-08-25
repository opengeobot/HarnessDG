package com.modelhub.api.controller;

import com.modelhub.api.support.Principals;
import com.modelhub.identity.security.CurrentPrincipal;
import com.modelhub.identity.service.RbacAdminService;
import com.modelhub.identity.service.RbacAdminService.PermissionView;
import com.modelhub.identity.service.RbacAdminService.RoleView;
import com.modelhub.shared.web.ApiEnvelope;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * 管理端 RBAC 端点（管理后台计划 §五）：角色/权限管理与指派。
 * 仅 platform_admin（服务层二次校验）；写操作要求 Idempotency-Key 并回显。
 */
@RestController
@RequestMapping("/api/v1/admin")
public class AdminRolesController {

    /** POST /roles 请求体。 */
    public record CreateRoleBody(String code, String description, List<String> permissions) {}

    /** PATCH /roles/{roleId} 请求体：字段为 null 表示不修改。 */
    public record UpdateRoleBody(String description, List<String> permissions) {}

    /** POST /users/{userId}/roles 请求体。 */
    public record AssignRoleBody(String roleCode) {}

    private final RbacAdminService rbac;

    public AdminRolesController(RbacAdminService rbac) {
        this.rbac = rbac;
    }

    @GetMapping("/roles")
    public ResponseEntity<ApiEnvelope<Map<String, Object>>> listRoles(HttpServletRequest request) {
        CurrentPrincipal actor = Principals.requireCurrent(request);
        List<RoleView> roles = rbac.listRoles(actor);
        return ResponseEntity.ok(ApiEnvelope.ok(Map.of("items", roles)));
    }

    @PostMapping("/roles")
    public ResponseEntity<ApiEnvelope<RoleView>> createRole(@RequestBody CreateRoleBody body,
                                                            @RequestHeader("Idempotency-Key") String idempotencyKey,
                                                            HttpServletRequest request) {
        CurrentPrincipal actor = Principals.requireCurrent(request);
        RoleView role = rbac.createRole(actor, body.code(), body.description(), body.permissions());
        return ResponseEntity.status(HttpStatus.CREATED).header("Idempotency-Key", idempotencyKey)
                .body(ApiEnvelope.created(role));
    }

    @PatchMapping("/roles/{roleId:[0-9a-fA-F-]{36}}")
    public ResponseEntity<ApiEnvelope<RoleView>> updateRole(@PathVariable("roleId") UUID roleId,
                                                            @RequestBody UpdateRoleBody body,
                                                            @RequestHeader("If-Match") String ifMatch,
                                                            @RequestHeader("Idempotency-Key") String idempotencyKey,
                                                            HttpServletRequest request) {
        CurrentPrincipal actor = Principals.requireCurrent(request);
        RoleView role = rbac.updateRole(actor, roleId, body.description(), body.permissions(), ifMatch);
        return ResponseEntity.ok().header("Idempotency-Key", idempotencyKey)
                .header("ETag", com.modelhub.shared.web.ETags.ofVersion(role.version()))
                .body(ApiEnvelope.ok(role));
    }

    @DeleteMapping("/roles/{roleId:[0-9a-fA-F-]{36}}")
    public ResponseEntity<Void> deleteRole(@PathVariable("roleId") UUID roleId,
                                           @RequestHeader("Idempotency-Key") String idempotencyKey,
                                           HttpServletRequest request) {
        CurrentPrincipal actor = Principals.requireCurrent(request);
        rbac.deleteRole(actor, roleId);
        return ResponseEntity.noContent().header("Idempotency-Key", idempotencyKey).build();
    }

    @GetMapping("/permissions")
    public ResponseEntity<ApiEnvelope<Map<String, Object>>> listPermissions(HttpServletRequest request) {
        CurrentPrincipal actor = Principals.requireCurrent(request);
        List<PermissionView> permissions = rbac.listPermissions(actor);
        return ResponseEntity.ok(ApiEnvelope.ok(Map.of("items", permissions)));
    }

    /** 给用户指派角色：禁止自我指派（403），重复指派 409。 */
    @PostMapping("/users/{userId:[0-9a-fA-F-]{36}}/roles")
    public ResponseEntity<ApiEnvelope<Map<String, Object>>> assignRole(@PathVariable("userId") UUID userId,
                                                                       @RequestBody AssignRoleBody body,
                                                                       @RequestHeader("Idempotency-Key") String idempotencyKey,
                                                                       HttpServletRequest request) {
        CurrentPrincipal actor = Principals.requireCurrent(request);
        List<String> roles = rbac.assignRole(actor, userId, body.roleCode());
        return ResponseEntity.status(HttpStatus.CREATED).header("Idempotency-Key", idempotencyKey)
                .body(ApiEnvelope.created(Map.of("roles", roles)));
    }

    /** 吊销用户角色：禁止自我吊销（403），最后 platform_admin 守卫 409。 */
    @PostMapping("/users/{userId:[0-9a-fA-F-]{36}}/roles/{roleCode}:revoke")
    public ResponseEntity<ApiEnvelope<Map<String, Object>>> revokeRole(@PathVariable("userId") UUID userId,
                                                                       @PathVariable("roleCode") String roleCode,
                                                                       @RequestHeader("Idempotency-Key") String idempotencyKey,
                                                                       HttpServletRequest request) {
        CurrentPrincipal actor = Principals.requireCurrent(request);
        List<String> roles = rbac.revokeRole(actor, userId, roleCode);
        return ResponseEntity.ok().header("Idempotency-Key", idempotencyKey)
                .body(ApiEnvelope.ok(Map.of("roles", roles)));
    }
}
