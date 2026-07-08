package com.aihub.transfer.application;

import com.aihub.asset.domain.Asset;
import com.aihub.asset.domain.AssetRepository;
import com.aihub.asset.domain.Visibility;
import com.aihub.audit.application.AuditService;
import com.aihub.authorization.application.AuthorizationService;
import com.aihub.shared.error.NotFoundException;
import com.aihub.transfer.domain.StoragePort;
import com.aihub.version.domain.Artifact;
import com.aihub.version.domain.Version;
import com.aihub.version.domain.VersionRepository;
import com.aihub.version.domain.VersionStatus;
import java.net.URL;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("DownloadApplicationService")
class DownloadApplicationServiceTest {

    @Mock
    private VersionRepository versionRepository;
    @Mock
    private AssetRepository assetRepository;
    @Mock
    private StoragePort storagePort;
    @Mock
    private AuthorizationService authorizationService;
    @Mock
    private AuditService auditService;

    private DownloadApplicationService service;

    @BeforeEach
    void setUp() {
        service = new DownloadApplicationService(versionRepository, assetRepository,
                storagePort, authorizationService, auditService);
    }

    @Test
    @DisplayName("签发 GIT_DVC 票据 — 整版本下载")
    void shouldIssueGitDvcTicket() {
        Version version = createPublishedVersion();
        Asset asset = createAsset();
        when(versionRepository.findByVersionId("ver_1")).thenReturn(Optional.of(version));
        when(assetRepository.findByAssetId("asset_1")).thenReturn(Optional.of(asset));

        var ticket = service.issueTicket("ver_1", null);

        assertThat(ticket.method()).isEqualTo("GIT_DVC");
        assertThat(ticket.presignedUrl()).isNull();
        verify(authorizationService).requirePermission("asset:read");
        verify(auditService).record(any());
    }

    @Test
    @DisplayName("签发 PRESIGNED_URL 票据 — 单工件下载")
    void shouldIssuePresignedUrlTicket() throws Exception {
        Version version = createPublishedVersion();
        Asset asset = createAsset();
        Artifact artifact = new Artifact("art_1", "ver_1", "model.bin",
                null, null, "sha256abc", 1024L, "application/octet-stream");

        when(versionRepository.findByVersionId("ver_1")).thenReturn(Optional.of(version));
        when(assetRepository.findByAssetId("asset_1")).thenReturn(Optional.of(asset));
        when(versionRepository.listArtifactsByVersion("ver_1")).thenReturn(List.of(artifact));
        when(storagePort.presignDownload(anyString(), anyString(), any(Duration.class)))
                .thenReturn(new URL("http://localhost:9000/dvc-cache/asset_1/v1/model.bin?presigned"));

        var ticket = service.issueTicket("ver_1", "art_1");

        assertThat(ticket.method()).isEqualTo("PRESIGNED_URL");
        assertThat(ticket.presignedUrl()).contains("presigned");
        assertThat(ticket.fileName()).isEqualTo("model.bin");
        assertThat(ticket.fileSize()).isEqualTo(1024L);
        assertThat(ticket.expiresAt()).isAfter(Instant.now().plusSeconds(14 * 60));
        verify(auditService).record(any());
    }

    @Test
    @DisplayName("非 PUBLISHED 版本应拒绝下载")
    void shouldRejectNonPublishedVersion() {
        Version version = Version.createDraft("ver_1", "asset_1", "v1", "principal_1");
        when(versionRepository.findByVersionId("ver_1")).thenReturn(Optional.of(version));

        assertThatThrownBy(() -> service.issueTicket("ver_1", null))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("PUBLISHED");
    }

    @Test
    @DisplayName("版本不存在应抛出 NotFoundException")
    void shouldThrowNotFoundWhenVersionMissing() {
        when(versionRepository.findByVersionId("ver_1")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.issueTicket("ver_1", null))
                .isInstanceOf(NotFoundException.class);
    }

    @Test
    @DisplayName("工件不存在应抛出 NotFoundException")
    void shouldThrowNotFoundWhenArtifactMissing() {
        Version version = createPublishedVersion();
        Asset asset = createAsset();
        when(versionRepository.findByVersionId("ver_1")).thenReturn(Optional.of(version));
        when(assetRepository.findByAssetId("asset_1")).thenReturn(Optional.of(asset));
        when(versionRepository.listArtifactsByVersion("ver_1")).thenReturn(List.of());

        assertThatThrownBy(() -> service.issueTicket("ver_1", "art_missing"))
                .isInstanceOf(NotFoundException.class);
    }

    private Version createPublishedVersion() {
        Instant now = Instant.now();
        return new Version("ver_1", "asset_1", "v1", VersionStatus.PUBLISHED,
                "commit_sha", "digest", "v1.0", now, "admin_1",
                "notes", 1L, "creator_1", now, now);
    }

    private Asset createAsset() {
        return Asset.create("asset_1", com.aihub.asset.domain.AssetType.MODEL,
                "org_1", null, "testorg", "test-asset",
                "Test Asset", "Description", Visibility.PUBLIC,
                List.of("creator_1"), List.of(), List.of(), null,
                null, null, null, "creator_1");
    }
}
