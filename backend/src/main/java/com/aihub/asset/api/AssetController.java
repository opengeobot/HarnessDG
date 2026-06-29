/*
 * 功能: 资产 REST 适配器，提供资产登记、查询、更新、删除与检索接口。
 * 时间: 2026-06-29
 * 作者: AxeXie
 */
package com.aihub.asset.api;

import com.aihub.asset.application.AssetApplicationService;
import com.aihub.asset.application.AssetSummaryView;
import com.aihub.asset.application.AssetView;
import com.aihub.asset.application.CreateAssetCommand;
import com.aihub.asset.application.UpdateAssetCommand;
import com.aihub.asset.domain.AssetType;
import com.aihub.shared.api.ApiResponse;
import com.aihub.shared.api.CursorPage;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
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

    public AssetController(AssetApplicationService assetService) {
        this.assetService = assetService;
    }

    /**
     * 登记新资产并开通仓库。
     */
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<AssetView> createAsset(@RequestBody CreateAssetRequest request) {
        CreateAssetCommand command = AssetRequestMapper.toCreateCommand(request, AssetApiContext.principalId());
        return AssetApiContext.respond(assetService.createAsset(command));
    }

    /**
     * 检索资产摘要（游标分页）。
     */
    @GetMapping
    public ApiResponse<CursorPage<AssetSummaryView>> searchAssets(
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) AssetType type,
            @RequestParam(required = false) String namespace,
            @RequestParam(required = false) String framework,
            @RequestParam(required = false) String task,
            @RequestParam(required = false) String format,
            @RequestParam(required = false) String modality,
            @RequestParam(required = false) String tag,
            @RequestParam(required = false) String owner,
            @RequestParam(required = false, defaultValue = "false") boolean includeArchived,
            @RequestParam(required = false) String cursor,
            @RequestParam(required = false, defaultValue = "0") int limit) {
        return AssetApiContext.respond(assetService.searchAssets(AssetRequestMapper.toSearchQuery(
                keyword, type, namespace, framework, task, format, modality, tag, owner,
                includeArchived, cursor, limit, AssetApiContext.principalId())));
    }

    /**
     * 查询资产详情。
     */
    @GetMapping("/{assetId}")
    public ApiResponse<AssetView> getAsset(@PathVariable String assetId) {
        return AssetApiContext.respond(assetService.getAsset(assetId, AssetApiContext.principalId()));
    }

    /**
     * 更新资产可变元数据。
     */
    @PatchMapping("/{assetId}")
    public ApiResponse<AssetView> updateAsset(@PathVariable String assetId,
                                              @RequestBody UpdateAssetRequest request) {
        UpdateAssetCommand command = AssetRequestMapper.toUpdateCommand(request, AssetApiContext.principalId());
        return AssetApiContext.respond(assetService.updateAsset(assetId, command));
    }

    /**
     * 逻辑删除资产。
     */
    @DeleteMapping("/{assetId}")
    public ApiResponse<Void> deleteAsset(@PathVariable String assetId) {
        assetService.deleteAsset(assetId, AssetApiContext.principalId());
        return AssetApiContext.respond(null);
    }
}
