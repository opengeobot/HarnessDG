/*
 * 功能: 资源 ACL 管理 REST 适配器，提供资源 ACL 列表/创建/删除接口。
 * 时间: 2026-06-30
 * 作者: AxeXie
 */
package com.aihub.authorization.api;

import com.aihub.authorization.api.AuthorizationRequests.CreateResourceAclRequest;
import com.aihub.authorization.application.AuthorizationDtos.CreateResourceAclCommand;
import com.aihub.authorization.application.AuthorizationDtos.ResourceAclView;
import com.aihub.authorization.application.AuthorizationService;
import com.aihub.authorization.application.ResourceAclApplicationService;
import com.aihub.authorization.domain.Permissions;
import com.aihub.job.application.IdempotencyService;
import com.aihub.shared.api.ApiResponse;
import com.aihub.shared.error.ValidationException;
import com.aihub.shared.idempotency.IdempotencyKey;
import com.aihub.shared.idempotency.IdempotencySupport;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * 资源 ACL 管理 REST 适配器。
 *
 * <p>读操作要求 {@code authorization:read}，写操作要求 {@code authorization:manage}（平台作用域），fail-closed。
 */
@RestController
@RequestMapping("/api/v1/system")
public class ResourceAclController {

    private final ResourceAclApplicationService aclService;
    private final AuthorizationService authorizationService;
    private final IdempotencyService idempotencyService;
    private final IdempotencySupport idempotency;

    public ResourceAclController(ResourceAclApplicationService aclService,
                                 AuthorizationService authorizationService,
                                 IdempotencyService idempotencyService,
                                 ObjectMapper objectMapper) {
        this.aclService = aclService;
        this.authorizationService = authorizationService;
        this.idempotencyService = idempotencyService;
        this.idempotency = new IdempotencySupport(objectMapper);
    }

    /**
     * 查询全部资源 ACL。
     */
    @GetMapping("/resource-acls")
    public ApiResponse<List<ResourceAclView>> listAcls() {
        authorizationService.requirePermission(Permissions.AUTHORIZATION_READ);
        return AuthorizationApiContext.respond(aclService.listAcls());
    }

    /**
     * 创建资源 ACL。
     */
    @PostMapping("/resource-acls")
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<ResourceAclView> createAcl(
            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKeyValue,
            @RequestBody CreateResourceAclRequest request) {
        authorizationService.requirePermission(Permissions.AUTHORIZATION_MANAGE);
        if (request == null) {
            throw new ValidationException("request body is required");
        }
        IdempotencyKey key = idempotency.buildKey(idempotencyKeyValue,
                AuthorizationApiContext.principalId(), "POST", "/api/v1/system/resource-acls");
        String fingerprint = idempotency.sha256Digest(request);
        AtomicReference<ResourceAclView> ref = new AtomicReference<>();
        idempotencyService.execute(key, fingerprint, () -> {
            ResourceAclView view = aclService.createAcl(new CreateResourceAclCommand(
                    request.principalId(), request.resourceType(), request.resourceId(),
                    request.permissionCodes() == null ? List.of() : request.permissionCodes()));
            ref.set(view);
            return new IdempotencyService.IdempotencyResponse(201, idempotency.serialize(view));
        });
        return AuthorizationApiContext.respond(ref.get());
    }

    /**
     * 删除资源 ACL。
     */
    @DeleteMapping("/resource-acls/{aclId}")
    public ApiResponse<Void> deleteAcl(
            @PathVariable String aclId,
            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKeyValue) {
        authorizationService.requirePermission(Permissions.AUTHORIZATION_MANAGE);
        IdempotencyKey key = idempotency.buildKey(idempotencyKeyValue,
                AuthorizationApiContext.principalId(), "DELETE",
                "/api/v1/system/resource-acls/" + aclId);
        idempotencyService.execute(key, null, () -> {
            aclService.deleteAcl(aclId);
            return new IdempotencyService.IdempotencyResponse(200, "");
        });
        return AuthorizationApiContext.respond(null);
    }
}
