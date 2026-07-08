package com.aihub.mcp.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.aihub.asset.application.AssetApplicationService;
import com.aihub.asset.application.AssetView;
import com.aihub.asset.domain.AssetStatus;
import com.aihub.asset.domain.AssetType;
import com.aihub.asset.domain.ProvisioningStatus;
import com.aihub.asset.domain.Visibility;
import com.aihub.shared.api.CursorPage;
import com.aihub.shared.identity.PrincipalContext;
import com.aihub.shared.identity.PrincipalContextHolder;
import com.aihub.shared.identity.PrincipalType;
import com.aihub.transfer.application.DownloadApplicationService;
import com.aihub.version.application.VersionApplicationService;
import com.aihub.version.application.VersionView;
import com.aihub.version.domain.VersionStatus;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * {@link McpToolCatalog} 单元测试。
 */
class McpToolCatalogTest {

    private AssetApplicationService assetService;
    private VersionApplicationService versionService;
    private DownloadApplicationService downloadService;
    private McpToolCatalog catalog;

    @BeforeEach
    void setUp() {
        assetService = mock(AssetApplicationService.class);
        versionService = mock(VersionApplicationService.class);
        downloadService = mock(DownloadApplicationService.class);
        catalog = new McpToolCatalog(assetService, versionService, downloadService, true);

        // 设置 PrincipalContext
        PrincipalContextHolder.set(new PrincipalContext("usr_test", PrincipalType.USER,
                null, null, null, null, Set.of("asset:read", "asset:manage"),
                0, null, null, null));
    }

    @AfterEach
    void tearDown() {
        PrincipalContextHolder.clear();
    }

    @Test
    void listToolsShouldReturnAllRegisteredTools() {
        List<McpToolCatalog.ToolDefinition> tools = catalog.listTools();
        assertThat(tools).isNotEmpty();
        assertThat(tools.stream().map(McpToolCatalog.ToolDefinition::name).toList())
                .contains("asset_search", "asset_get", "asset_list_versions",
                        "asset_get_version", "asset_request_download", "asset_create_draft");
    }

    @Test
    void callToolAssetSearchShouldDelegateToService() {
        when(assetService.searchAssets(any())).thenReturn(
                new CursorPage<>(List.of(), null, false));

        Object result = catalog.callTool("asset_search", Map.of("keyword", "test"));
        assertThat(result).isNotNull();
    }

    @Test
    void callToolAssetGetShouldDelegateToService() {
        AssetView view = new AssetView("ast_1", AssetType.MODEL, "nlp", "org_1", null,
                "test-model", "Test Model", "desc", Visibility.INTERNAL, AssetStatus.ACTIVE,
                List.of(), null, null, List.of(), List.of(), "Apache-2.0", null, null, null,
                ProvisioningStatus.COMPLETED, null, 0L, null, null, null,
                Instant.now(), Instant.now());
        when(assetService.getAsset(anyString(), anyString())).thenReturn(view);

        Object result = catalog.callTool("asset_get", Map.of("assetId", "ast_1"));
        assertThat(result).isEqualTo(view);
    }

    @Test
    void callToolAssetListVersionsShouldDelegateToService() {
        when(versionService.listVersions(anyString(), any(), anyInt())).thenReturn(
                new CursorPage<>(List.of(), null, false));

        Object result = catalog.callTool("asset_list_versions",
                Map.of("assetId", "ast_1"));
        assertThat(result).isNotNull();
    }

    @Test
    void callToolUnknownShouldThrow() {
        assertThatThrownBy(() -> catalog.callTool("unknown_tool", Map.of()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Unknown tool");
    }

    @Test
    void writeToolsDisabledShouldRejectWriteCalls() {
        McpToolCatalog disabledCatalog = new McpToolCatalog(
                assetService, versionService, downloadService, false);

        assertThatThrownBy(() -> disabledCatalog.callTool("asset_create_draft",
                Map.of("assetId", "ast_1", "version", "v1")))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("disabled");
    }
}
