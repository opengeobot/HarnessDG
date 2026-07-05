package com.aihub.mcp.application;

import com.aihub.asset.application.AssetApplicationService;
import com.aihub.asset.application.AssetView;
import com.aihub.authorization.application.AuthorizationService;
import com.aihub.shared.identity.PrincipalContextHolder;
import com.aihub.version.application.VersionApplicationService;
import com.aihub.version.application.VersionView;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.springframework.stereotype.Service;

/**
 * MCP Resources 处理器。
 *
 * <p>支持 URI 格式：
 * <ul>
 *   <li>{@code aih://asset/{id}} — 资产详情</li>
 *   <li>{@code aih://asset/{id}/version/{v}} — 版本详情</li>
 *   <li>{@code aih://asset/{id}/card} — 资产 Card（README）</li>
 * </ul>
 *
 * <p>每次 read 实时授权（asset:read）。
 */
@Service
public class McpResourceHandler {

    private static final Pattern ASSET_URI = Pattern.compile("^aih://asset/([^/]+)$");
    private static final Pattern VERSION_URI = Pattern.compile("^aih://asset/([^/]+)/version/([^/]+)$");
    private static final Pattern CARD_URI = Pattern.compile("^aih://asset/([^/]+)/card$");

    private final AssetApplicationService assetService;
    private final VersionApplicationService versionService;
    private final AuthorizationService authorizationService;

    public McpResourceHandler(AssetApplicationService assetService,
                              VersionApplicationService versionService,
                              AuthorizationService authorizationService) {
        this.assetService = assetService;
        this.versionService = versionService;
        this.authorizationService = authorizationService;
    }

    /** 列出可用 Resources（返回模式描述）。 */
    public List<Map<String, Object>> listResources() {
        return List.of(
                resourceTemplate("aih://asset/{assetId}", "Asset details",
                        "application/json"),
                resourceTemplate("aih://asset/{assetId}/version/{versionId}", "Version details",
                        "application/json"),
                resourceTemplate("aih://asset/{assetId}/card", "Asset card (README)",
                        "text/markdown"));
    }

    /** 读取指定 URI 的 Resource 内容。 */
    public Map<String, Object> readResource(String uri) {
        String principalId = PrincipalContextHolder.current()
                .map(c -> c.principalId())
                .orElse("anonymous");

        // asset/{id}/version/{v}
        Matcher versionMatcher = VERSION_URI.matcher(uri);
        if (versionMatcher.matches()) {
            String versionId = versionMatcher.group(2);
            authorizationService.requirePermission("asset:read");
            VersionView view = versionService.getVersion(versionId);
            return contentEntry(uri, "application/json", view);
        }

        // asset/{id}/card
        Matcher cardMatcher = CARD_URI.matcher(uri);
        if (cardMatcher.matches()) {
            String assetId = cardMatcher.group(1);
            authorizationService.requirePermission("asset:read");
            AssetView view = assetService.getAsset(assetId, principalId);
            String card = (view.card() != null && view.card().readme() != null)
                    ? view.card().readme() : "(no card available)";
            return contentEntry(uri, "text/markdown", card);
        }

        // asset/{id}
        Matcher assetMatcher = ASSET_URI.matcher(uri);
        if (assetMatcher.matches()) {
            String assetId = assetMatcher.group(1);
            authorizationService.requirePermission("asset:read");
            AssetView view = assetService.getAsset(assetId, principalId);
            return contentEntry(uri, "application/json", view);
        }

        throw new IllegalArgumentException("Unknown resource URI: " + uri);
    }

    private static Map<String, Object> resourceTemplate(String uriTemplate, String name, String mimeType) {
        Map<String, Object> template = new LinkedHashMap<>();
        template.put("uriTemplate", uriTemplate);
        template.put("name", name);
        template.put("mimeType", mimeType);
        return template;
    }

    private static Map<String, Object> contentEntry(String uri, String mimeType, Object content) {
        return Map.of("uri", uri, "mimeType", mimeType, "text", String.valueOf(content));
    }
}
