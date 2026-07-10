package com.aihub.mcp.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.aihub.asset.application.AssetApplicationService;
import com.aihub.asset.application.AssetSummaryView;
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
import com.aihub.transfer.application.DownloadApplicationService.DownloadTicket;
import com.aihub.transfer.application.UploadApplicationService;
import com.aihub.version.application.PublishApplicationService;
import com.aihub.version.application.VersionApplicationService;
import com.aihub.version.application.VersionView;
import com.aihub.version.domain.Version;
import com.aihub.version.domain.VersionRepository;
import com.aihub.version.domain.VersionStatus;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
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
    private VersionRepository versionRepository;
    private DownloadApplicationService downloadService;
    private UploadApplicationService uploadService;
    private PublishApplicationService publishService;
    private McpToolCatalog catalog;

    @BeforeEach
    void setUp() {
        assetService = mock(AssetApplicationService.class);
        versionService = mock(VersionApplicationService.class);
        versionRepository = mock(VersionRepository.class);
        downloadService = mock(DownloadApplicationService.class);
        uploadService = mock(UploadApplicationService.class);
        publishService = mock(PublishApplicationService.class);
        catalog = new McpToolCatalog(assetService, versionService, versionRepository, downloadService,
                uploadService, publishService, true);

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
                        "asset_get_version", "asset_request_download", "asset_create_draft",
                        "asset_create_upload_session", "asset_complete_upload",
                        "asset_get_upload_status", "asset_publish_version", "asset_delete");
    }

    @Test
    void listVisibleToolsShouldExcludeHighRiskTools() {
        List<String> visible = catalog.listVisibleTools().stream()
                .map(McpToolCatalog.ToolDefinition::name)
                .toList();
        assertThat(visible).doesNotContain("asset_publish_version", "asset_delete");
    }

    @Test
    void callToolAssetSearchShouldIncludeCoordinateMatchedFieldsAndLatestPublished() {
        AssetSummaryView summary = new AssetSummaryView(
                "ast_1", "aih://nlp/model/test", AssetType.MODEL, "nlp", "org_1", null,
                "test", "Test", "desc", Visibility.INTERNAL, AssetStatus.ACTIVE,
                List.of(), List.of(), List.of(), "Apache-2.0", null, null, null, null,
                Instant.now(), List.of("name"));
        when(assetService.searchAssets(any())).thenReturn(
                new CursorPage<>(List.of(summary), null, false));
        when(versionRepository.findLatestPublishedByAssetIds(any()))
                .thenReturn(Map.of("ast_1", new Version(
                        "ver_1", "ast_1", "1.0.0", VersionStatus.PUBLISHED,
                        null, null, null, Instant.now(), "usr", null,
                        0L, "usr", Instant.now(), Instant.now())));

        @SuppressWarnings("unchecked")
        Map<String, Object> result = (Map<String, Object>) catalog.callTool("asset_search",
                Map.of("keyword", "test"));
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> items = (List<Map<String, Object>>) result.get("items");

        assertThat(items).hasSize(1);
        assertThat(items.get(0)).containsEntry("coordinate", "aih://nlp/model/test");
        assertThat(items.get(0)).containsEntry("matchedFields", List.of("name"));
        assertThat(items.get(0)).containsEntry("latestPublished", "1.0.0");
        verify(versionRepository).findLatestPublishedByAssetIds(any());
    }

    @Test
    void callToolAssetGetShouldDelegateToService() {
        AssetView view = new AssetView("ast_1", "aih://nlp/model/test-model", AssetType.MODEL, "nlp", "org_1", null,
                "test-model", "Test Model", "desc", Visibility.INTERNAL, AssetStatus.ACTIVE,
                List.of(), null, null, List.of(), List.of(), "Apache-2.0", null, null, null,
                ProvisioningStatus.COMPLETED, null, 0L, null, null, null,
                Instant.now(), Instant.now());
        when(assetService.getAsset(anyString(), anyString())).thenReturn(view);

        @SuppressWarnings("unchecked")
        Map<String, Object> result = (Map<String, Object>) catalog.callTool("asset_get", Map.of("assetId", "ast_1"));
        assertThat(result).containsEntry("assetId", "ast_1");
        assertThat(result).containsEntry("coordinate", "aih://nlp/model/test-model");
    }

    @Test
    void callToolAssetListVersionsShouldDelegateToService() {
        when(versionService.listVersions(anyString(), any(), anyInt())).thenReturn(
                new CursorPage<>(List.of(), null, false));

        Object result = catalog.callTool("asset_list_versions", Map.of("assetId", "ast_1"));
        assertThat(result).isNotNull();
    }

    @Test
    void callToolDownloadShouldReturnHandleWithoutPresignedUrl() {
        when(downloadService.issueTicket(eq("ver_1"), eq("art_1")))
                .thenReturn(new DownloadTicket("PRESIGNED_URL", "https://secret.example/obj",
                        Instant.now(), "file.bin", 42L, null, null, null, "ast_1"));

        McpDownloadHandle handle = (McpDownloadHandle) catalog.callTool("asset_request_download",
                Map.of("versionId", "ver_1", "artifactId", "art_1"));

        assertThat(handle.versionId()).isEqualTo("ver_1");
        assertThat(handle.method()).isEqualTo("PRESIGNED_URL");
        assertThat(handle.fileName()).isEqualTo("file.bin");
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
                assetService, versionService, versionRepository, downloadService, uploadService,
                publishService, false);

        assertThatThrownBy(() -> disabledCatalog.callTool("asset_create_draft",
                Map.of("assetId", "ast_1", "version", "v1")))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("disabled");
    }

    @Test
    void agentShouldBeDeniedPublishAndDeleteTools() {
        PrincipalContextHolder.set(new PrincipalContext("agt_1", PrincipalType.AGENT,
                null, null, null, null, Set.of("asset:publish", "asset:delete", "asset:manage"),
                0, null, null, null));

        assertThatThrownBy(() -> catalog.callTool("asset_publish_version", Map.of("versionId", "ver_1")))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("denied for agents");
        assertThatThrownBy(() -> catalog.callTool("asset_delete", Map.of("assetId", "ast_1")))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("denied for agents");
    }
}
