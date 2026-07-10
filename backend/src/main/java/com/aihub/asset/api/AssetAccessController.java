/*
 * 功能: 资产 ACL REST 适配器，提供资产级显式授权列表/创建/删除接口。
 * 时间: 2026-07-10
 * 作者: AxeXie
 */
package com.aihub.asset.api;

import com.aihub.asset.application.AssetApplicationService;
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
 * 资产 ACL REST 适配器。
 *
 * <p>复用 {@link ResourceAclApplicationService}，资源类型固定为 {@code ASSET}。
 * 读操作要求 {@code asset:read} 且可访问资产；写操作要求 {@code asset:manage}，fail-closed。
 */
@RestController
@RequestMapping("/api/v1/assets/{assetId}/access")
public class AssetAccessController {

    private final ResourceAclApplicationService aclService;
    private final AssetApplicationService assetService;
    private final AuthorizationService authorizationService;
    private final IdempotencyService idempotencyService;
    private final IdempotencySupport idempotency;

    public AssetAccessController(ResourceAclApplicationService aclService,
                                 AssetApplicationService assetService,
                                 AuthorizationService authorizationService,
                                 IdempotencyService idempotencyService,
                                 ObjectMapper objectMapper) {
        this.aclService = aclService;
        this.assetService = assetService;
        this.authorizationService = authorizationService;
        this.idempotencyService = idempotencyService;
        this.idempotency = new IdempotencySupport(objectMapper);
    }

    /**
     * 列出资产上的显式 ACL。
     */
    @GetMapping
    public ApiResponse<List<ResourceAclView>> listAssetAccess(@PathVariable String assetId) {
        authorizationService.requirePermission(Permissions.ASSET_READ);
        assetService.getAsset(assetId, AssetApiContext.principalId());
        return AssetApiContext.respond(aclService.listAclsByResource("ASSET", assetId));
    }

    /**
     * 为资产创建显式 ACL。
     */
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<ResourceAclView> createAssetAccess(
            @PathVariable String assetId,
            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKeyValue,
            @RequestBody CreateAssetAccessRequest request) {
        authorizationService.requirePermission(Permissions.ASSET_MANAGE);
        assetService.getAsset(assetId, AssetApiContext.principalId());
        if (request == null) {
            throw new ValidationException("request body is required");
        }
        IdempotencyKey key = idempotency.buildKey(idempotencyKeyValue,
                AssetApiContext.principalId(), "POST", "/api/v1/assets/" + assetId + "/access");
        String fingerprint = idempotency.sha256Digest(request);
        AtomicReference<ResourceAclView> ref = new AtomicReference<>();
        idempotencyService.execute(key, fingerprint, () -> {
            ResourceAclView view = aclService.createAcl(new CreateResourceAclCommand(
                    request.principalId(), "ASSET", assetId,
                    request.permissionCodes() == null ? List.of() : request.permissionCodes()));
            ref.set(view);
            return new IdempotencyService.IdempotencyResponse(201, idempotency.serialize(view));
        });
        return AssetApiContext.respond(ref.get());
    }

    /**
     * 删除资产上的显式 ACL 条目。
     */
    @DeleteMapping("/{aclId}")
    public ApiResponse<Void> deleteAssetAccess(
            @PathVariable String assetId,
            @PathVariable String aclId,
            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKeyValue) {
        authorizationService.requirePermission(Permissions.ASSET_MANAGE);
        assetService.getAsset(assetId, AssetApiContext.principalId());
        IdempotencyKey key = idempotency.buildKey(idempotencyKeyValue,
                AssetApiContext.principalId(), "DELETE",
                "/api/v1/assets/" + assetId + "/access/" + aclId);
        idempotencyService.execute(key, null, () -> {
            aclService.deleteAcl(aclId);
            return new IdempotencyService.IdempotencyResponse(200, "");
        });
        return AssetApiContext.respond(null);
    }
}
