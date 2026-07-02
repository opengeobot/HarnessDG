/*
 * 功能: 角色与权限管理 REST 适配器，提供角色列表/创建/更新/删除与权限定义列表接口。
 * 时间: 2026-06-30
 * 作者: AxeXie
 */
package com.aihub.authorization.api;

import com.aihub.authorization.api.AuthorizationRequests.CreateRoleRequest;
import com.aihub.authorization.api.AuthorizationRequests.UpdateRoleRequest;
import com.aihub.authorization.application.AuthorizationDtos.CreateRoleCommand;
import com.aihub.authorization.application.AuthorizationDtos.PermissionView;
import com.aihub.authorization.application.AuthorizationDtos.RoleView;
import com.aihub.authorization.application.AuthorizationDtos.UpdateRoleCommand;
import com.aihub.authorization.application.AuthorizationService;
import com.aihub.authorization.application.PermissionQueryApplicationService;
import com.aihub.authorization.application.RoleManagementApplicationService;
import com.aihub.authorization.domain.Permissions;
import com.aihub.shared.api.ApiResponse;
import com.aihub.shared.error.ValidationException;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * 角色与权限管理 REST 适配器。
 *
 * <p>读操作要求 {@code authorization:read}，写操作要求 {@code authorization:manage}（平台作用域），
 * 经统一 {@link AuthorizationService} 校验，fail-closed。
 */
@RestController
@RequestMapping("/api/v1/system")
public class RoleController {

    private final RoleManagementApplicationService roleService;
    private final PermissionQueryApplicationService permissionService;
    private final AuthorizationService authorizationService;

    public RoleController(RoleManagementApplicationService roleService,
                          PermissionQueryApplicationService permissionService,
                          AuthorizationService authorizationService) {
        this.roleService = roleService;
        this.permissionService = permissionService;
        this.authorizationService = authorizationService;
    }

    /**
     * 查询角色。
     */
    @GetMapping("/roles")
    public ApiResponse<List<RoleView>> listRoles() {
        authorizationService.requirePermission(Permissions.AUTHORIZATION_READ);
        return AuthorizationApiContext.respond(roleService.listRoles());
    }

    /**
     * 创建业务角色。
     */
    @PostMapping("/roles")
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<RoleView> createRole(@RequestBody CreateRoleRequest request) {
        authorizationService.requirePermission(Permissions.AUTHORIZATION_MANAGE);
        if (request == null) {
            throw new ValidationException("request body is required");
        }
        RoleView view = roleService.createRole(new CreateRoleCommand(
                request.roleCode(), request.roleName(),
                request.permissionCodes() == null ? List.of() : request.permissionCodes()));
        return AuthorizationApiContext.respond(view);
    }

    /**
     * 更新自定义角色名称与权限集合。
     */
    @PatchMapping("/roles/{roleId}")
    public ApiResponse<RoleView> updateRole(@PathVariable String roleId,
                                            @RequestBody UpdateRoleRequest request) {
        authorizationService.requirePermission(Permissions.AUTHORIZATION_MANAGE);
        if (request == null || request.expectedVersion() == null) {
            throw new ValidationException("expectedVersion is required");
        }
        RoleView view = roleService.updateRole(roleId, new UpdateRoleCommand(
                request.roleName(), request.permissionCodes(), request.expectedVersion()));
        return AuthorizationApiContext.respond(view);
    }

    /**
     * 删除无有效绑定的自定义角色。
     */
    @DeleteMapping("/roles/{roleId}")
    public ApiResponse<Void> deleteRole(@PathVariable String roleId) {
        authorizationService.requirePermission(Permissions.AUTHORIZATION_MANAGE);
        roleService.deleteRole(roleId);
        return AuthorizationApiContext.respond(null);
    }

    /**
     * 查询系统权限定义。
     */
    @GetMapping("/permissions")
    public ApiResponse<List<PermissionView>> listPermissions() {
        authorizationService.requirePermission(Permissions.AUTHORIZATION_READ);
        return AuthorizationApiContext.respond(permissionService.listPermissions());
    }
}
