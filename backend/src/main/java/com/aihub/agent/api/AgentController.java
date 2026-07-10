/*
 * 功能: Agent REST 适配器，提供 /api/v1/agent/* 只读端点，复用与 MCP 相同的应用服务与授权。
 * 时间: 2026-07-10
 * 作者: AxeXie
 */
package com.aihub.agent.api;

import com.aihub.asset.api.AssetApiContext;
import com.aihub.asset.application.AssetApplicationService;
import com.aihub.asset.application.AssetSearchQuery;
import com.aihub.asset.application.AssetSummaryView;
import com.aihub.asset.application.AssetView;
import com.aihub.asset.domain.AssetType;
import com.aihub.authorization.application.AuthorizationService;
import com.aihub.authorization.domain.Permissions;
import com.aihub.mcp.application.McpDownloadHandle;
import com.aihub.shared.api.ApiResponse;
import com.aihub.shared.api.CursorPage;
import com.aihub.shared.error.AuthorizationException;
import com.aihub.shared.error.ErrorCode;
import com.aihub.shared.identity.PrincipalContext;
import com.aihub.shared.identity.PrincipalContextHolder;
import com.aihub.shared.identity.PrincipalType;
import com.aihub.transfer.application.DownloadApplicationService;
import com.aihub.version.application.VersionApplicationService;
import com.aihub.version.application.VersionView;
import com.aihub.version.domain.Version;
import com.aihub.version.domain.VersionRepository;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Agent REST 适配器（只读 Profile 最小集）。
 *
 * <p>薄控制器：请求映射 + JWT 授权（与 MCP 等价的 Scope + Agent 工具白名单），业务规则在应用服务内完成。
 */
@RestController
@RequestMapping("/api/v1/agent")
public class AgentController {

    private final AssetApplicationService assetService;
    private final VersionApplicationService versionService;
    private final VersionRepository versionRepository;
    private final DownloadApplicationService downloadService;
    private final AuthorizationService authorizationService;

    public AgentController(AssetApplicationService assetService,
                           VersionApplicationService versionService,
                           VersionRepository versionRepository,
                           DownloadApplicationService downloadService,
                           AuthorizationService authorizationService) {
        this.assetService = assetService;
        this.versionService = versionService;
        this.versionRepository = versionRepository;
        this.downloadService = downloadService;
        this.authorizationService = authorizationService;
    }

    /** 搜索资产（等价 MCP {@code asset_search}）。 */
    @GetMapping("/assets/search")
    public ApiResponse<CursorPage<Map<String, Object>>> searchAssets(
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) AssetType type,
            @RequestParam(required = false) String namespace,
            @RequestParam(required = false) String tagId,
            @RequestParam(required = false) String cursor,
            @RequestParam(required = false, defaultValue = "20") int limit) {
        requireAgentAccess("asset_search", Permissions.ASSET_READ);
        String principalId = AssetApiContext.principalId();
        AssetSearchQuery query = new AssetSearchQuery(
                keyword, type, namespace, null, null, null, null, null,
                null, null, null, null, tagId, null, null, null,
                null, null, null, false, cursor, limit, principalId);
        CursorPage<AssetSummaryView> page = assetService.searchAssets(query);
        Map<String, Version> latestByAsset = versionRepository.findLatestPublishedByAssetIds(
                page.items().stream().map(AssetSummaryView::assetId).toList());
        List<Map<String, Object>> items = page.items().stream()
                .map(summary -> toSummaryMap(summary, latestByAsset.get(summary.assetId())))
                .toList();
        return AssetApiContext.respond(new CursorPage<>(items, page.nextCursor(), page.hasMore()));
    }

    /** 查询资产详情（等价 MCP {@code asset_get}）。 */
    @GetMapping("/assets/{assetId}")
    public ApiResponse<Map<String, Object>> getAsset(@PathVariable String assetId) {
        requireAgentAccess("asset_get", Permissions.ASSET_READ);
        AssetView view = assetService.getAsset(assetId, AssetApiContext.principalId());
        Map<String, Object> result = toDetailMap(view);
        versionRepository.findLatestPublishedByAssetId(assetId)
                .ifPresent(v -> result.put("latestPublished", v.version()));
        return AssetApiContext.respond(result);
    }

    /** 列出版本（等价 MCP {@code asset_list_versions}）。 */
    @GetMapping("/assets/{assetId}/versions")
    public ApiResponse<CursorPage<VersionView>> listVersions(
            @PathVariable String assetId,
            @RequestParam(required = false) String cursor,
            @RequestParam(required = false, defaultValue = "20") int limit) {
        requireAgentAccess("asset_list_versions", Permissions.ASSET_READ);
        return AssetApiContext.respond(versionService.listVersions(assetId, cursor, limit));
    }

    /** 查询版本详情（等价 MCP {@code asset_get_version}）。 */
    @GetMapping("/versions/{versionId}")
    public ApiResponse<VersionView> getVersion(@PathVariable String versionId) {
        requireAgentAccess("asset_get_version", Permissions.ASSET_READ);
        return AssetApiContext.respond(versionService.getVersion(versionId));
    }

    /** 签发下载票据 handle（等价 MCP {@code asset_request_download}，不含 presignedUrl）。 */
    @PostMapping("/versions/{versionId}/download")
    public ApiResponse<McpDownloadHandle> requestDownload(
            @PathVariable String versionId,
            @RequestBody(required = false) DownloadRequest request) {
        requireAgentAccess("asset_request_download", Permissions.ASSET_READ);
        String artifactId = request == null ? null : request.artifactId();
        var ticket = downloadService.issueTicket(versionId, artifactId);
        return AssetApiContext.respond(McpDownloadHandle.fromTicket(versionId, artifactId, ticket));
    }

    public record DownloadRequest(String artifactId) {
    }

    private void requireAgentAccess(String mcpTool, String requiredPermission) {
        PrincipalContext context = PrincipalContextHolder.current()
                .filter(ctx -> ctx.principalId() != null)
                .orElseThrow(() -> new AuthorizationException(
                        ErrorCode.AUTH_UNAUTHENTICATED, "no authenticated principal", Map.of()));
        authorizationService.requirePermission(context, Permissions.MCP_INVOKE);
        authorizationService.requirePermission(context, requiredPermission);
        if (context.principalType() == PrincipalType.AGENT) {
            authorizationService.requireToolAllowed(context.principalId(), mcpTool);
        }
    }

    private static Map<String, Object> toSummaryMap(AssetSummaryView summary, Version latestPublished) {
        Map<String, Object> item = new LinkedHashMap<>();
        item.put("assetId", summary.assetId());
        item.put("coordinate", summary.coordinate());
        item.put("type", summary.type());
        item.put("namespace", summary.namespace());
        item.put("name", summary.name());
        item.put("displayName", summary.displayName());
        item.put("description", summary.description());
        item.put("visibility", summary.visibility());
        item.put("status", summary.status());
        item.put("license", summary.license());
        item.put("matchedFields", summary.matchedFields());
        item.put("updatedAt", summary.updatedAt());
        if (latestPublished != null) {
            item.put("latestPublished", latestPublished.version());
        }
        return item;
    }

    private static Map<String, Object> toDetailMap(AssetView view) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("assetId", view.assetId());
        result.put("coordinate", view.coordinate());
        result.put("type", view.type());
        result.put("namespace", view.namespace());
        result.put("name", view.name());
        result.put("displayName", view.displayName());
        result.put("description", view.description());
        result.put("visibility", view.visibility());
        result.put("status", view.status());
        result.put("license", view.license());
        result.put("organizationId", view.organizationId());
        result.put("owners", view.owners());
        result.put("tags", view.tags());
        result.put("tagIds", view.tagIds());
        result.put("provisioningStatus", view.provisioningStatus());
        result.put("createdAt", view.createdAt());
        result.put("updatedAt", view.updatedAt());
        return result;
    }
}
