/*
 * 功能: 系统用户管理 REST 适配器，提供本地用户的查询、创建、更新、启停与重置口令接口。
 * 时间: 2026-06-30
 * 作者: AxeXie
 */
package com.aihub.identity.api;

import com.aihub.identity.api.IdentityRequests.CreateUserRequest;
import com.aihub.identity.api.IdentityRequests.ResetPasswordRequest;
import com.aihub.identity.api.IdentityRequests.UpdateUserRequest;
import com.aihub.identity.api.IdentityResponses.PageResultUserPayload;
import com.aihub.identity.api.IdentityResponses.UserPayload;
import com.aihub.authorization.application.AuthorizationService;
import com.aihub.authorization.domain.Permissions;
import com.aihub.identity.application.IdentityCommands;
import com.aihub.identity.application.UserManagementApplicationService;
import com.aihub.identity.application.UserView;
import com.aihub.identity.domain.UserStatus;
import com.aihub.job.application.IdempotencyService;
import com.aihub.shared.api.ApiResponse;
import com.aihub.shared.api.PageResult;
import com.aihub.shared.idempotency.IdempotencyKey;
import com.aihub.shared.idempotency.IdempotencySupport;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.concurrent.atomic.AtomicReference;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * 系统用户管理 REST 适配器。
 *
 * <p>所有端点要求认证 + 统一 {@link AuthorizationService} 权限校验（fail-closed）。
 * P0-B 合并规则：JWT 粗粒度 Scope 与 RBAC 角色解析任一命中所需权限即放行。
 */
@RestController
@RequestMapping("/api/v1/system/users")
public class SystemUserController {

    private final UserManagementApplicationService userService;
    private final AuthorizationService authorizationService;
    private final IdempotencyService idempotencyService;
    private final IdempotencySupport idempotency;

    public SystemUserController(UserManagementApplicationService userService,
                                AuthorizationService authorizationService,
                                IdempotencyService idempotencyService,
                                ObjectMapper objectMapper) {
        this.userService = userService;
        this.authorizationService = authorizationService;
        this.idempotencyService = idempotencyService;
        this.idempotency = new IdempotencySupport(objectMapper);
    }

    /**
     * 分页查询本地用户。
     */
    @GetMapping
    public ApiResponse<PageResultUserPayload> listUsers(
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) UserStatus status,
            @RequestParam(required = false, defaultValue = "1") int page,
            @RequestParam(required = false, defaultValue = "20") int size) {
        authorizationService.requirePermission(Permissions.USER_READ);
        PageResult<UserView> result = userService.listUsers(keyword, status, page, size);
        PageResultUserPayload payload = new PageResultUserPayload(
                result.items().stream().map(UserPayload::from).toList(),
                result.page(), result.pageSize(), result.total());
        return IdentityApiContext.respond(payload);
    }

    /**
     * 创建本地用户。
     */
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<UserPayload> createUser(
            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKeyValue,
            @RequestBody CreateUserRequest request) {
        authorizationService.requirePermission(Permissions.USER_MANAGE);
        IdempotencyKey key = idempotency.buildKey(idempotencyKeyValue,
                IdentityApiContext.principalId(), "POST", "/api/v1/system/users");
        String fingerprint = idempotency.sha256Digest(request);
        AtomicReference<UserPayload> ref = new AtomicReference<>();
        idempotencyService.execute(key, fingerprint, () -> {
            UserView view = userService.createUser(new IdentityCommands.CreateUserCommand(
                    request.username(), request.displayName(), request.email(), request.locale(),
                    request.temporaryPassword(), request.scopes()));
            UserPayload payload = UserPayload.from(view);
            ref.set(payload);
            return new IdempotencyService.IdempotencyResponse(201, idempotency.serialize(payload));
        });
        return IdentityApiContext.respond(ref.get());
    }

    /**
     * 查询用户详情。
     */
    @GetMapping("/{userId}")
    public ApiResponse<UserPayload> getUser(@PathVariable String userId) {
        authorizationService.requirePermission(Permissions.USER_READ);
        return IdentityApiContext.respond(UserPayload.from(userService.getUser(userId)));
    }

    /**
     * 更新用户资料。
     */
    @PatchMapping("/{userId}")
    public ApiResponse<UserPayload> updateUser(
            @PathVariable String userId,
            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKeyValue,
            @RequestBody UpdateUserRequest request) {
        authorizationService.requirePermission(Permissions.USER_MANAGE);
        IdempotencyKey key = idempotency.buildKey(idempotencyKeyValue,
                IdentityApiContext.principalId(), "PATCH", "/api/v1/system/users/" + userId);
        String fingerprint = idempotency.sha256Digest(request);
        AtomicReference<UserPayload> ref = new AtomicReference<>();
        idempotencyService.execute(key, fingerprint, () -> {
            UserView view = userService.updateUser(userId, new IdentityCommands.UpdateUserCommand(
                    request.displayName(), request.email(), request.locale()));
            UserPayload payload = UserPayload.from(view);
            ref.set(payload);
            return new IdempotencyService.IdempotencyResponse(200, idempotency.serialize(payload));
        });
        return IdentityApiContext.respond(ref.get());
    }

    /**
     * 启用用户。
     */
    @PostMapping("/{userId}:enable")
    public ApiResponse<Void> enableUser(
            @PathVariable String userId,
            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKeyValue) {
        authorizationService.requirePermission(Permissions.USER_MANAGE);
        IdempotencyKey key = idempotency.buildKey(idempotencyKeyValue,
                IdentityApiContext.principalId(), "POST", "/api/v1/system/users/" + userId + ":enable");
        idempotencyService.execute(key, null, () -> {
            userService.enableUser(userId);
            return new IdempotencyService.IdempotencyResponse(200, "");
        });
        return IdentityApiContext.respond(null);
    }

    /**
     * 禁用用户并吊销其 Token。
     */
    @PostMapping("/{userId}:disable")
    public ApiResponse<Void> disableUser(
            @PathVariable String userId,
            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKeyValue) {
        authorizationService.requirePermission(Permissions.USER_MANAGE);
        IdempotencyKey key = idempotency.buildKey(idempotencyKeyValue,
                IdentityApiContext.principalId(), "POST", "/api/v1/system/users/" + userId + ":disable");
        idempotencyService.execute(key, null, () -> {
            userService.disableUser(userId);
            return new IdempotencyService.IdempotencyResponse(200, "");
        });
        return IdentityApiContext.respond(null);
    }

    /**
     * 重置口令：设置临时口令、强制下次修改并吊销现有 Token。
     */
    @PostMapping("/{userId}:reset-password")
    public ApiResponse<Void> resetPassword(
            @PathVariable String userId,
            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKeyValue,
            @RequestBody ResetPasswordRequest request) {
        authorizationService.requirePermission(Permissions.USER_MANAGE);
        IdempotencyKey key = idempotency.buildKey(idempotencyKeyValue,
                IdentityApiContext.principalId(), "POST",
                "/api/v1/system/users/" + userId + ":reset-password");
        String fingerprint = idempotency.sha256Digest(request);
        idempotencyService.execute(key, fingerprint, () -> {
            userService.resetPassword(userId, request.temporaryPassword());
            return new IdempotencyService.IdempotencyResponse(200, "");
        });
        return IdentityApiContext.respond(null);
    }
}
