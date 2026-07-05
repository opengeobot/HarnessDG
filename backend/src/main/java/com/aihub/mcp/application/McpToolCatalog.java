package com.aihub.mcp.application;

import com.aihub.asset.application.AssetApplicationService;
import com.aihub.asset.application.AssetSearchQuery;
import com.aihub.asset.application.AssetSummaryView;
import com.aihub.asset.application.AssetView;
import com.aihub.asset.domain.AssetType;
import com.aihub.shared.api.CursorPage;
import com.aihub.shared.identity.PrincipalContextHolder;
import com.aihub.transfer.application.DownloadApplicationService;
import com.aihub.version.application.VersionApplicationService;
import com.aihub.version.application.VersionView;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
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
 */
@Service
public class McpToolCatalog {

    private static final Logger LOG = LoggerFactory.getLogger(McpToolCatalog.class);

    private final Map<String, ToolDefinition> tools = new ConcurrentHashMap<>();
    private final Map<String, ToolHandler> handlers = new ConcurrentHashMap<>();
    private final boolean writeToolsEnabled;

    public McpToolCatalog(
            AssetApplicationService assetService,
            VersionApplicationService versionService,
            DownloadApplicationService downloadService,
            @Value("${mcp.writeTools.enabled:false}") boolean writeToolsEnabled) {
        this.writeToolsEnabled = writeToolsEnabled;
        registerReadOnlyTools(assetService, versionService, downloadService);
        registerWriteTools(assetService, versionService);
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

    /** 列出所有已注册 Tool 定义。 */
    public List<ToolDefinition> listTools() {
        return Collections.unmodifiableList(tools.values().stream().toList());
    }

    /** 调用指定 Tool。 */
    public Object callTool(String name, Map<String, Object> arguments) {
        ToolDefinition def = tools.get(name);
        if (def == null) {
            throw new IllegalArgumentException("Unknown tool: " + name);
        }
        if (def.write() && !writeToolsEnabled) {
            throw new IllegalStateException("Write tools are disabled: " + name);
        }
        ToolHandler handler = handlers.get(name);
        if (handler == null) {
            throw new IllegalStateException("No handler registered for tool: " + name);
        }
        return handler.call(arguments);
    }

    // ---- 只读工具注册 ----

    private void registerReadOnlyTools(AssetApplicationService assetService,
                                       VersionApplicationService versionService,
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
                false, "asset:read",
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
                            tagId, null, null, null, false, null, limit, principalId);
                    CursorPage<AssetSummaryView> page = assetService.searchAssets(query);
                    return Map.of("items", page.items(), "nextCursor", nullSafe(page.nextCursor()),
                            "hasMore", page.hasMore());
                });

        // asset_get
        register("asset_get",
                "Get a single asset by ID.",
                schema(Map.of("assetId", Map.of("type", "string"))),
                false, "asset:read",
                args -> {
                    String principalId = currentPrincipalId();
                    AssetView view = assetService.getAsset(str(args, "assetId"), principalId);
                    return view;
                });

        // asset_list_versions
        register("asset_list_versions",
                "List versions for a given asset.",
                schema(Map.of(
                        "assetId", Map.of("type", "string"),
                        "cursor", Map.of("type", "string"),
                        "limit", Map.of("type", "integer", "minimum", 1, "maximum", 100, "default", 20))),
                false, "asset:read",
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
                false, "asset:read",
                args -> versionService.getVersion(str(args, "versionId")));

        // asset_request_download
        register("asset_request_download",
                "Request a download ticket for a published version. Returns a handle, not a presigned URL.",
                schema(Map.of(
                        "versionId", Map.of("type", "string"),
                        "artifactId", Map.of("type", "string",
                                "description", "Optional artifact ID; omit for whole version (GIT_DVC)."))),
                false, "asset:read",
                args -> {
                    String versionId = str(args, "versionId");
                    String artifactId = str(args, "artifactId");
                    return downloadService.issueTicket(versionId, artifactId);
                });
    }

    // ---- 写工具注册 ----

    private void registerWriteTools(AssetApplicationService assetService,
                                    VersionApplicationService versionService) {
        // asset_create_draft
        register("asset_create_draft",
                "Create a new draft version for an asset. Write tool, disabled by default.",
                schema(Map.of(
                        "assetId", Map.of("type", "string"),
                        "version", Map.of("type", "string"))),
                true, "asset:manage",
                args -> {
                    String principalId = currentPrincipalId();
                    return versionService.createDraftVersion(
                            str(args, "assetId"), str(args, "version"), principalId);
                });
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

    private static Object nullSafe(Object v) {
        return v != null ? v : "";
    }
}
