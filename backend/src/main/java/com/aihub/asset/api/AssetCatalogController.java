/*
 * 功能: 模型/数据集兼容型语义检索入口，固定类型委托资产检索。
 * 时间: 2026-06-29
 * 作者: AxeXie
 */
package com.aihub.asset.api;

import com.aihub.asset.application.AssetApplicationService;
import com.aihub.asset.application.AssetSummaryView;
import com.aihub.asset.domain.AssetType;
import com.aihub.shared.api.ApiResponse;
import com.aihub.shared.api.CursorPage;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 模型/数据集兼容型语义检索入口。
 *
 * <p>对应设计 8.1 节的 {@code GET /models}、{@code GET /datasets}：固定资产类型后复用统一资产检索，
 * 便于按语义直达模型或数据集列表。
 */
@RestController
public class AssetCatalogController {

    private final AssetApplicationService assetService;

    public AssetCatalogController(AssetApplicationService assetService) {
        this.assetService = assetService;
    }

    /**
     * 检索模型列表。
     */
    @GetMapping("/api/v1/models")
    public ApiResponse<CursorPage<AssetSummaryView>> searchModels(
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String namespace,
            @RequestParam(required = false) String framework,
            @RequestParam(required = false) String task,
            @RequestParam(required = false) String tagId,
            @RequestParam(required = false) String owner,
            @RequestParam(required = false) String sensitivity,
            @RequestParam(required = false, defaultValue = "false") boolean includeArchived,
            @RequestParam(required = false) String cursor,
            @RequestParam(required = false, defaultValue = "0") int limit) {
        return AssetApiContext.respond(assetService.searchAssets(AssetRequestMapper.toSearchQuery(
                keyword, AssetType.MODEL, namespace, null, null, null, null, null,
                framework, task, null, null, tagId, owner,
                null, sensitivity, null, null, null,
                includeArchived, cursor, limit, AssetApiContext.principalId())));
    }

    /**
     * 检索数据集列表。
     */
    @GetMapping("/api/v1/datasets")
    public ApiResponse<CursorPage<AssetSummaryView>> searchDatasets(
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String namespace,
            @RequestParam(required = false) String format,
            @RequestParam(required = false) String modality,
            @RequestParam(required = false) String language,
            @RequestParam(required = false) String tagId,
            @RequestParam(required = false) String owner,
            @RequestParam(required = false) String sensitivity,
            @RequestParam(required = false, defaultValue = "false") boolean includeArchived,
            @RequestParam(required = false) String cursor,
            @RequestParam(required = false, defaultValue = "0") int limit) {
        return AssetApiContext.respond(assetService.searchAssets(AssetRequestMapper.toSearchQuery(
                keyword, AssetType.DATASET, namespace, null, null, null, null, null,
                null, null, format, modality, tagId, owner,
                language, sensitivity, null, null, null,
                includeArchived, cursor, limit, AssetApiContext.principalId())));
    }
}
