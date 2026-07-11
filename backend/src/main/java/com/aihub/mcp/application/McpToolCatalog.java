package com.aihub.mcp.application;

import com.aihub.asset.application.AssetApplicationService;
import com.aihub.asset.application.AssetSearchQuery;
import com.aihub.asset.application.AssetSummaryView;
import com.aihub.asset.application.AssetView;
import com.aihub.asset.domain.AssetType;
import com.aihub.authorization.domain.Permissions;
import com.aihub.shared.api.CursorPage;
import com.aihub.shared.identity.PrincipalContext;
import com.aihub.shared.identity.PrincipalContextHolder;
import com.aihub.shared.identity.PrincipalType;
import com.aihub.transfer.application.DownloadApplicationService;
import com.aihub.transfer.application.UploadApplicationService;
import com.aihub.version.application.PublishApplicationService;
import com.aihub.version.application.VersionApplicationService;
import com.aihub.version.application.VersionQueryService;
import com.aihub.version.application.VersionView;
import com.aihub.version.domain.Version;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

/**
 * MCP Tool 注册目录与调用分发。
 *
 * <p>每个 Tool 声明 name/description/inputSchema/write/requiredPermission，
 * 调用时复用与 REST 相同的 Application Service，不绕过权限。
 *
 * <p>写工具受 {@code mcp.writeTools.enabled} 开关控制，默认关闭。
 * {@link #listVisibleTools()} 默认隐藏写工具；高风险 publish/delete 永不暴露给 Agent。
 */
@Service
public class McpToolCatalog {

    private static final Logger LOG = LoggerFactory.getLogger(McpToolCatalog.class);

    /** 高风险工具：永不出现在 tools/list，Agent 调用一律拒绝。 */
    static final Set<String> AGENT_DENIED_TOOLS = Set.of("asset_publish_version", "asset_delete");

    private final Map<String, ToolDefinition> tools = new ConcurrentHashMap<>();
    private final Map<String, ToolHandler> handlers = new ConcurrentHashMap<>();
    private final boolean writeToolsEnabled;

    public McpToolCatalog(
            AssetApplicationService assetService,
            VersionApplicationService versionService,
            VersionQueryService versionQueryService,
            DownloadApplicationService downloadService,
            UploadApplicationService uploadService,
            PublishApplicationService publishService,
            @Value("${mcp.writeTools.enabled:false}") boolean writeToolsEnabled) {
        this.writeToolsEnabled = writeToolsEnabled;
        registerReadOnlyTools(assetService, versionService, versionQueryService, downloadService);
        registerWriteTools(assetService, versionService, uploadService, publishService);
        LOG.info("MCP Tool Catalog initialized: {} tools registered (writeTools={})",
                tools.size(), writeToolsEnabled);
    }

    /** Tool 定义 record。 */
    public record ToolDefinition(
            String name,
            String description,
            Map<String, Object> inputSchema,
            boolean write,
            String requiredPermission) {
    }

    /** Tool 调用函数接口。 */
    @FunctionalInterface
    interface ToolHandler {
        Object call(Map<String, Object> arguments);
    }

    /** 列出全部已注册 Tool 定义（含默认关闭的写工具）。 */
    public List<ToolDefinition> listTools() {
        return Collections.unmodifiableList(tools.values().stream().toList());
    }

    /** 列出对当前调用方可发现的 Tool（写工具默认隐藏，高风险工具永不列出）。 */
    public List<ToolDefinition> listVisibleTools() {
        return tools.values().stream()
                .filter(this::isDiscoverable)
                .toList();
    }

    /** 查找 Tool 定义。 */
    public Optional<ToolDefinition> findTool(String name) {
        return Optional.ofNullable(tools.get(name));
    }

    /** 调用指定 Tool。 */
    public Object callTool(String name, Map<String, Object> arguments) {
        ToolDefinition def = tools.get(name);
        if (def == null) {
            throw new IllegalArgumentException("Unknown tool: " + name);
        }
        enforceAgentPolicy(name);
        if (def.write() && !writeToolsEnabled) {
            throw new IllegalStateException("Write tools are disabled: " + name);
        }
        ToolHandler handler = handlers.get(name);
        if (handler == null) {
            throw new IllegalStateException("No handler registered for tool: " + name);
        }
        return handler.call(arguments);
    }

    private void enforceAgentPolicy(String toolName) {
        PrincipalContext context = PrincipalContextHolder.current().orElse(null);
        if (context == null || context.principalType() != PrincipalType.AGENT) {
            return;
        }
        if (AGENT_DENIED_TOOLS.contains(toolName)) {
            throw new IllegalStateException("Tool denied for agents: " + toolName);
        }
        ToolDefinition def = tools.get(toolName);
        if (def != null && Permissions.HIGH_RISK_ACTIONS.contains(def.requiredPermission())) {
            throw new IllegalStateException("High-risk tool denied for agents: " + toolName);
        }
    }

    private boolean isDiscoverable(ToolDefinition tool) {
        if (AGENT_DENIED_TOOLS.contains(tool.name())) {
            return false;
        }
        if (tool.write() && !writeToolsEnabled) {
            return false;
        }
        return true;
    }

    // ---- 只读工具注册 ----

    private void registerReadOnlyTools(AssetApplicationService assetService,
                                       VersionApplicationService versionService,
                                       VersionQueryService versionQueryService,
                                       DownloadApplicationService downloadService) {
        // asset_search
        register("asset_search",
                "Search assets by keyword, type, namespace with access-control filtering.",
                schema(Map.of(
                        "keyword", Map.of("type", "string"),
                        "type", Map.of("type", "string", "enum", List.of("MODEL", "DATASET")),
                        "namespace", Map.of("type", "string"),
                        "tagId", Map.of("type", "string"),
                        "cursor", Map.of("type", "string"),
                        "limit", Map.of("type", "integer", "minimum", 1, "maximum", 100, "default", 20))),
                false, Permissions.ASSET_READ,
                args -> {
                    String principalId = currentPrincipalId();
                    String keyword = str(args, "keyword");
                    AssetType type = args.containsKey("type") ? AssetType.valueOf(str(args, "type")) : null;
                    String namespace = str(args, "namespace");
                    String tagId = str(args, "tagId");
                    String cursor = str(args, "cursor");
                    int limit = intArg(args, "limit", 20);
                    AssetSearchQuery query = new AssetSearchQuery(
                            keyword, type, namespace, null, null, null, null, null,
                            null, null, null, null, tagId, null, null, null,
                            null, null, null, false, null, limit, principalId);
                    CursorPage<AssetSummaryView> page = assetService.searchAssets(query);
                    Map<String, Version> latestByAsset = versionQueryService.findLatestPublishedByAssetIds(
                            Set.copyOf(page.items().stream().map(AssetSummaryView::assetId).toList()));
                    List<Map<String, Object>> items = page.items().stream()
                            .map(summary -> toSearchItem(summary, latestByAsset.get(summary.assetId())))
                            .toList();
                    return Map.of("items", items, "nextCursor", nullSafe(page.nextCursor()),
                            "hasMore", page.hasMore());
                });

        // asset_get
        register("asset_get",
                "Get a single asset by ID.",
                schema(Map.of("assetId", Map.of("type", "string"))),
                false, Permissions.ASSET_READ,
                args -> {
                    String principalId = currentPrincipalId();
                    AssetView view = assetService.getAsset(str(args, "assetId"), principalId);
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
                    result.put("tagIds", view.tagIds());
                    versionQueryService.findLatestPublishedByAssetId(view.assetId())
                            .ifPresent(v -> result.put("latestPublished", v.version()));
                    return result;
                });

        // asset_list_versions
        register("asset_list_versions",
                "List versions for a given asset.",
                schema(Map.of(
                        "assetId", Map.of("type", "string"),
                        "cursor", Map.of("type", "string"),
                        "limit", Map.of("type", "integer", "minimum", 1, "maximum", 100, "default", 20))),
                false, Permissions.ASSET_READ,
                args -> {
                    String assetId = str(args, "assetId");
                    String cursor = str(args, "cursor");
                    int limit = intArg(args, "limit", 20);
                    CursorPage<VersionView> page = versionService.listVersions(assetId, cursor, limit);
                    return Map.of("items", page.items(), "nextCursor", nullSafe(page.nextCursor()),
                            "hasMore", page.hasMore());
                });

        // asset_get_version
        register("asset_get_version",
                "Get version details by version ID.",
                schema(Map.of("versionId", Map.of("type", "string"))),
                false, Permissions.ASSET_READ,
                args -> versionService.getVersion(str(args, "versionId")));

        // asset_request_download
        register("asset_request_download",
                "Request a download ticket for a published version. Returns a handle, not a presigned URL.",
                schema(Map.of(
                        "versionId", Map.of("type", "string"),
                        "artifactId", Map.of("type", "string",
                                "description", "Optional artifact ID; omit for whole version (GIT_DVC)."))),
                false, Permissions.ASSET_READ,
                args -> {
                    String versionId = str(args, "versionId");
                    String artifactId = str(args, "artifactId");
                    var ticket = downloadService.issueTicket(versionId, artifactId);
                    return McpDownloadHandle.fromTicket(versionId, artifactId, ticket);
                });
    }

    // ---- 写工具注册 ----

    private void registerWriteTools(AssetApplicationService assetService,
                                    VersionApplicationService versionService,
                                    UploadApplicationService uploadService,
                                    PublishApplicationService publishService) {
        // asset_create_draft
        register("asset_create_draft",
                "Create a new draft version for an asset. Write tool, disabled by default.",
                schema(Map.of(
                        "assetId", Map.of("type", "string"),
                        "version", Map.of("type", "string"))),
                true, Permissions.ASSET_MANAGE,
                args -> {
                    String principalId = currentPrincipalId();
                    return versionService.createDraftVersion(
                            str(args, "assetId"), str(args, "version"), principalId);
                });

        // asset_create_upload_session
        register("asset_create_upload_session",
                "Create an upload session for a draft version. Write tool, disabled by default.",
                schema(Map.of(
                        "assetId", Map.of("type", "string"),
                        "versionId", Map.of("type", "string"),
                        "totalBytes", Map.of("type", "integer"),
                        "fileCount", Map.of("type", "integer"))),
                true, Permissions.ASSET_MANAGE,
                args -> {
                    String principalId = currentPrincipalId();
                    return uploadService.createSession(
                            str(args, "assetId"), str(args, "versionId"),
                            longArg(args, "totalBytes", 0), intArg(args, "fileCount", 1),
                            principalId);
                });

        // asset_complete_upload
        register("asset_complete_upload",
                "Complete an upload session. Write tool, disabled by default.",
                schema(Map.of(
                        "sessionId", Map.of("type", "string"))),
                true, Permissions.ASSET_MANAGE,
                args -> {
                    String principalId = currentPrincipalId();
                    return uploadService.completeSession(
                            str(args, "sessionId"), List.of(), List.of(), principalId);
                });

        // asset_get_upload_status
        register("asset_get_upload_status",
                "Get upload session status. Write tool, disabled by default.",
                schema(Map.of(
                        "sessionId", Map.of("type", "string"))),
                true, Permissions.ASSET_READ,
                args -> uploadService.getSession(str(args, "sessionId")));

        // asset_publish_version (high-risk, never for agents)
        register("asset_publish_version",
                "Publish an asset version. High-risk write tool, disabled by default.",
                schema(Map.of(
                        "versionId", Map.of("type", "string"))),
                true, Permissions.ASSET_PUBLISH,
                args -> publishService.submitPublishRequest(str(args, "versionId")));

        // asset_delete (high-risk, never for agents)
        register("asset_delete",
                "Delete an asset. High-risk write tool, disabled by default.",
                schema(Map.of(
                        "assetId", Map.of("type", "string"))),
                true, Permissions.ASSET_DELETE,
                args -> {
                    String principalId = currentPrincipalId();
                    assetService.deleteAsset(str(args, "assetId"), principalId);
                    return Map.of("deleted", true);
                });
    }

    private static Map<String, Object> toSearchItem(AssetSummaryView summary, Version latestPublished) {
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

    // ---- 辅助方法 ----

    private void register(String name, String description, Map<String, Object> inputSchema,
                          boolean write, String requiredPermission, ToolHandler handler) {
        tools.put(name, new ToolDefinition(name, description, inputSchema, write, requiredPermission));
        handlers.put(name, handler);
    }

    private static Map<String, Object> schema(Map<String, Object> properties) {
        Map<String, Object> s = new LinkedHashMap<>();
        s.put("type", "object");
        s.put("additionalProperties", false);
        s.put("properties", properties);
        return s;
    }

    private static String currentPrincipalId() {
        return PrincipalContextHolder.current()
                .map(c -> c.principalId())
                .orElse("anonymous");
    }

    private static String str(Map<String, Object> args, String key) {
        Object v = args.get(key);
        return v != null ? v.toString() : null;
    }

    private static int intArg(Map<String, Object> args, String key, int defaultValue) {
        Object v = args.get(key);
        if (v instanceof Number n) return n.intValue();
        if (v instanceof String s) {
            try { return Integer.parseInt(s); } catch (NumberFormatException e) { /* fall through */ }
        }
        return defaultValue;
    }

    private static long longArg(Map<String, Object> args, String key, long defaultValue) {
        Object v = args.get(key);
        if (v instanceof Number n) return n.longValue();
        if (v instanceof String s) {
            try { return Long.parseLong(s); } catch (NumberFormatException e) { /* fall through */ }
        }
        return defaultValue;
    }

    private static Object nullSafe(Object v) {
        return v != null ? v : "";
    }
}
