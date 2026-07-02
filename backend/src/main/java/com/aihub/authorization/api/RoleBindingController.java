/*
 * 功能: 角色绑定管理 REST 适配器，提供角色绑定列表/创建/删除接口。
 * 时间: 2026-06-30
 * 作者: AxeXie
 */
package com.aihub.authorization.api;

import com.aihub.authorization.api.AuthorizationRequests.CreateRoleBindingRequest;
import com.aihub.authorization.application.AuthorizationDtos.CreateRoleBindingCommand;
import com.aihub.authorization.application.AuthorizationDtos.RoleBindingView;
import com.aihub.authorization.application.AuthorizationService;
import com.aihub.authorization.application.RoleBindingApplicationService;
import com.aihub.authorization.domain.Permissions;
import com.aihub.shared.api.ApiResponse;
import com.aihub.shared.error.ValidationException;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * 角色绑定管理 REST 适配器。
 *
 * <p>读操作要求 {@code authorization:read}，写操作要求 {@code authorization:manage}（平台作用域），fail-closed。
 */
@RestController
@RequestMapping("/api/v1/system")
public class RoleBindingController {

    private final RoleBindingApplicationService bindingService;
    private final AuthorizationService authorizationService;

    public RoleBindingController(RoleBindingApplicationService bindingService,
                                 AuthorizationService authorizationService) {
        this.bindingService = bindingService;
        this.authorizationService = authorizationService;
    }

    /**
     * 查询全部角色绑定。
     */
    @GetMapping("/role-bindings")
    public ApiResponse<List<RoleBindingView>> listBindings() {
        authorizationService.requirePermission(Permissions.AUTHORIZATION_READ);
        return AuthorizationApiContext.respond(bindingService.listBindings());
    }

    /**
     * 创建角色绑定。
     */
    @PostMapping("/role-bindings")
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<RoleBindingView> createBinding(@RequestBody CreateRoleBindingRequest request) {
        authorizationService.requirePermission(Permissions.AUTHORIZATION_MANAGE);
        if (request == null) {
            throw new ValidationException("request body is required");
        }
        RoleBindingView view = bindingService.createBinding(new CreateRoleBindingCommand(
                request.principalId(), request.roleId(), request.scopeType(), request.scopeId()));
        return AuthorizationApiContext.respond(view);
    }

    /**
     * 删除角色绑定。
     */
    @DeleteMapping("/role-bindings/{bindingId}")
    public ApiResponse<Void> deleteBinding(@PathVariable String bindingId) {
        authorizationService.requirePermission(Permissions.AUTHORIZATION_MANAGE);
        bindingService.deleteBinding(bindingId);
        return AuthorizationApiContext.respond(null);
    }
}
