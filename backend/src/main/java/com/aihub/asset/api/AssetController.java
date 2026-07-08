/*
 * 功能: 资产 REST 适配器，提供资产登记、查询、更新、删除与检索接口。
 * 时间: 2026-06-29
 * 作者: AxeXie
 */
package com.aihub.asset.api;

import com.aihub.asset.application.AssetApplicationService;
import com.aihub.asset.application.AssetFacetView;
import com.aihub.asset.application.AssetSummaryView;
import com.aihub.asset.application.AssetView;
import com.aihub.asset.application.CreateAssetCommand;
import com.aihub.asset.application.UpdateAssetCommand;
import com.aihub.asset.domain.AssetType;
import com.aihub.job.application.IdempotencyService;
import com.aihub.shared.api.ApiResponse;
import com.aihub.shared.api.CursorPage;
import com.aihub.shared.idempotency.IdempotencyKey;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
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
 * 资产 REST 适配器。
 *
 * <p>作为适配层仅做请求映射与上下文透传，业务规则在应用服务/领域内完成；不直接访问 Mapper。
 */
@RestController
@RequestMapping("/api/v1/assets")
public class AssetController {

    private final AssetApplicationService assetService;
    private final IdempotencyService idempotencyService;
    private final ObjectMapper objectMapper;

    public AssetController(AssetApplicationService assetService,
                           IdempotencyService idempotencyService,
                           ObjectMapper objectMapper) {
        this.assetService = assetService;
        this.idempotencyService = idempotencyService;
        this.objectMapper = objectMapper;
    }

    /**
     * 登记新资产并开通仓库。支持 Idempotency-Key 幂等保护。
     */
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<AssetView> createAsset(
            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKeyValue,
            @RequestBody CreateAssetRequest request) {
        CreateAssetCommand command = AssetRequestMapper.toCreateCommand(request, AssetApiContext.principalId());
        IdempotencyKey key = buildIdempotencyKey(idempotencyKeyValue, "POST", "/api/v1/assets");
        String fingerprint = sha256Digest(request);
        var result = idempotencyService.execute(key, fingerprint, () -> {
            AssetView view = assetService.createAsset(command);
            return new IdempotencyService.IdempotencyResponse(201, serialize(view));
        });
        AssetView view = deserialize(result.response().body(), AssetView.class);
        return AssetApiContext.respond(view);
    }

    /**
     * 检索资产摘要（游标分页）。
     */
    @GetMapping
    public ApiResponse<CursorPage<AssetSummaryView>> searchAssets(
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) AssetType type,
            @RequestParam(required = false) String namespace,
            @RequestParam(required = false) String organizationId,
            @RequestParam(required = false) String projectId,
            @RequestParam(required = false) String visibility,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String teamId,
            @RequestParam(required = false) String framework,
            @RequestParam(required = false) String task,
            @RequestParam(required = false) String format,
            @RequestParam(required = false) String modality,
            @RequestParam(required = false) String tagId,
            @RequestParam(required = false) String owner,
            @RequestParam(required = false) String language,
            @RequestParam(required = false) String sensitivity,
            @RequestParam(required = false) List<String> taskCodes,
            @RequestParam(required = false) List<String> modalityCodes,
            @RequestParam(required = false) List<String> formatCodes,
            @RequestParam(required = false, defaultValue = "false") boolean includeArchived,
            @RequestParam(required = false) String cursor,
            @RequestParam(required = false, defaultValue = "0") int limit) {
        return AssetApiContext.respond(assetService.searchAssets(AssetRequestMapper.toSearchQuery(
                keyword, type, namespace, organizationId, projectId, visibility, status, teamId,
                framework, task, format, modality, tagId, owner,
                language, sensitivity, taskCodes, modalityCodes, formatCodes,
                includeArchived, cursor, limit, AssetApiContext.principalId())));
    }

    /**
     * 查询资产 Facet 统计（按维度分组计数，经权限过滤）。
     */
    @GetMapping("/facets")
    public ApiResponse<AssetFacetView> getAssetFacets(
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) AssetType type) {
        return AssetApiContext.respond(
                assetService.getAssetFacets(keyword, type, AssetApiContext.principalId()));
    }

    /**
     * 查询资产详情。
     */
    @GetMapping("/{assetId}")
    public ApiResponse<AssetView> getAsset(@PathVariable String assetId) {
        return AssetApiContext.respond(assetService.getAsset(assetId, AssetApiContext.principalId()));
    }

    /**
     * 更新资产可变元数据。支持 Idempotency-Key 幂等保护。
     */
    @PatchMapping("/{assetId}")
    public ApiResponse<AssetView> updateAsset(
            @PathVariable String assetId,
            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKeyValue,
            @RequestBody UpdateAssetRequest request) {
        UpdateAssetCommand command = AssetRequestMapper.toUpdateCommand(request, AssetApiContext.principalId());
        IdempotencyKey key = buildIdempotencyKey(idempotencyKeyValue, "PATCH", "/api/v1/assets/" + assetId);
        String fingerprint = sha256Digest(request);
        var result = idempotencyService.execute(key, fingerprint, () -> {
            AssetView view = assetService.updateAsset(assetId, command);
            return new IdempotencyService.IdempotencyResponse(200, serialize(view));
        });
        AssetView view = deserialize(result.response().body(), AssetView.class);
        return AssetApiContext.respond(view);
    }

    /**
     * 逻辑删除资产。支持 Idempotency-Key 幂等保护。
     */
    @DeleteMapping("/{assetId}")
    public ApiResponse<Void> deleteAsset(
            @PathVariable String assetId,
            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKeyValue) {
        IdempotencyKey key = buildIdempotencyKey(idempotencyKeyValue, "DELETE", "/api/v1/assets/" + assetId);
        var result = idempotencyService.execute(key, null, () -> {
            assetService.deleteAsset(assetId, AssetApiContext.principalId());
            return new IdempotencyService.IdempotencyResponse(200, "");
        });
        return AssetApiContext.respond(null);
    }

    /**
     * 弃用资产：仍可访问但检索降权。支持弃用原因与替代资产。
     */
    @PostMapping("/{assetId}/deprecate")
    public ApiResponse<AssetView> deprecateAsset(
            @PathVariable String assetId,
            @RequestBody(required = false) DeprecateAssetRequest request) {
        String reason = request != null ? request.deprecationReason() : null;
        String note = request != null ? request.deprecationNote() : null;
        String replacementId = request != null ? request.replacementAssetId() : null;
        return AssetApiContext.respond(
                assetService.deprecateAsset(assetId, AssetApiContext.principalId(),
                        reason, note, replacementId));
    }

    /**
     * 归档资产：默认不返回，仅管理员可恢复。
     */
    @PostMapping("/{assetId}/archive")
    public ApiResponse<AssetView> archiveAsset(@PathVariable String assetId) {
        return AssetApiContext.respond(
                assetService.archiveAsset(assetId, AssetApiContext.principalId()));
    }

    /**
     * 恢复资产：从弃用或归档状态恢复为活跃。
     */
    @PostMapping("/{assetId}/restore")
    public ApiResponse<AssetView> restoreAsset(@PathVariable String assetId) {
        return AssetApiContext.respond(
                assetService.restoreAsset(assetId, AssetApiContext.principalId()));
    }

    // ---- 幂等辅助方法 ----

    private IdempotencyKey buildIdempotencyKey(String value, String method, String path) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return new IdempotencyKey(value, AssetApiContext.principalId(), method, path);
    }

    private String sha256Digest(Object body) {
        try {
            byte[] bytes = objectMapper.writeValueAsBytes(body);
            byte[] hash = MessageDigest.getInstance("SHA-256").digest(bytes);
            return HexFormat.of().formatHex(hash);
        } catch (JsonProcessingException | NoSuchAlgorithmException e) {
            return "unknown";
        }
    }

    private String serialize(Object obj) {
        try {
            return objectMapper.writeValueAsString(obj);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("failed to serialize idempotency response", e);
        }
    }

    private <T> T deserialize(String json, Class<T> type) {
        try {
            return objectMapper.readValue(json, type);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("failed to deserialize idempotency response", e);
        }
    }
}
