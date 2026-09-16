package com.aihub.version.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.aihub.asset.domain.Asset;
import com.aihub.asset.domain.AssetRepository;
import com.aihub.asset.domain.AssetType;
import com.aihub.audit.application.AuditService;
import com.aihub.authorization.application.AuthorizationService;
import com.aihub.authorization.domain.Permissions;
import com.aihub.shared.api.CursorPage;
import com.aihub.shared.error.ConflictException;
import com.aihub.shared.error.NotFoundException;
import com.aihub.shared.error.ValidationException;
import com.aihub.shared.id.IdGenerator;
import com.aihub.shared.id.IdPrefix;
import com.aihub.version.domain.Version;
import com.aihub.version.domain.VersionRepository;
import com.aihub.version.domain.VersionStatus;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

/**
 * {@link VersionApplicationService} 单元测试。
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class VersionApplicationServiceTest {

    @Mock private VersionRepository versionRepository;
    @Mock private AssetRepository assetRepository;
    @Mock private AuthorizationService authorizationService;
    @Mock private AuditService auditService;
    @Mock private IdGenerator idGenerator;

    private VersionApplicationService service;

    @BeforeEach
    void setUp() {
        service = new VersionApplicationService(
                versionRepository, assetRepository, authorizationService, auditService, idGenerator);
        when(idGenerator.generate(any(IdPrefix.class))).thenReturn("ver_generated");
        // 默认资产为 MODEL 类型
        Asset defaultAsset = new Asset.Builder()
                .assetId("ast_01")
                .type(AssetType.MODEL)
                .build();
        when(assetRepository.findByAssetId("ast_01")).thenReturn(Optional.of(defaultAsset));
    }

    @Test
    void createDraftVersionInsertsAndAudits() {
        VersionView view = service.createDraftVersion("ast_01", "1.0.0", "usr_01");

        assertThat(view.versionId()).isEqualTo("ver_generated");
        assertThat(view.status()).isEqualTo(VersionStatus.DRAFT);
        verify(authorizationService).requirePermission(Permissions.ASSET_MANAGE);
        verify(versionRepository).insert(any(Version.class));
        verify(auditService).record(any());
    }

    @Test
    void createDraftVersionRejectsInvalidModelSemVer() {
        assertThatThrownBy(() -> service.createDraftVersion("ast_01", "1.0", "usr_01"))
                .isInstanceOf(ValidationException.class);

        assertThatThrownBy(() -> service.createDraftVersion("ast_01", "v1.0.0", "usr_01"))
                .isInstanceOf(ValidationException.class);

        assertThatThrownBy(() -> service.createDraftVersion("ast_01", "1.0.0-alpha", "usr_01"))
                .isInstanceOf(ValidationException.class);
    }

    @Test
    void createDraftVersionAcceptsDatasetSemVer() {
        Asset datasetAsset = new Asset.Builder()
                .assetId("ast_ds_01")
                .type(AssetType.DATASET)
                .build();
        when(assetRepository.findByAssetId("ast_ds_01")).thenReturn(Optional.of(datasetAsset));

        VersionView view = service.createDraftVersion("ast_ds_01", "1.0.0", "usr_01");
        assertThat(view.version()).isEqualTo("1.0.0");
    }

    @Test
    void createDraftVersionAcceptsDatasetDateVersion() {
        Asset datasetAsset = new Asset.Builder()
                .assetId("ast_ds_02")
                .type(AssetType.DATASET)
                .build();
        when(assetRepository.findByAssetId("ast_ds_02")).thenReturn(Optional.of(datasetAsset));

        VersionView view = service.createDraftVersion("ast_ds_02", "2024-01-15", "usr_01");
        assertThat(view.version()).isEqualTo("2024-01-15");
    }

    @Test
    void createDraftVersionRejectsInvalidDatasetVersion() {
        Asset datasetAsset = new Asset.Builder()
                .assetId("ast_ds_03")
                .type(AssetType.DATASET)
                .build();
        when(assetRepository.findByAssetId("ast_ds_03")).thenReturn(Optional.of(datasetAsset));

        assertThatThrownBy(() -> service.createDraftVersion("ast_ds_03", "2024/01/15", "usr_01"))
                .isInstanceOf(ValidationException.class);

        assertThatThrownBy(() -> service.createDraftVersion("ast_ds_03", "v1", "usr_01"))
                .isInstanceOf(ValidationException.class);
    }

    @Test
    void createDraftVersionRejectsDuplicateCoordinate() {
        Version existing = Version.createDraft("ver_existing", "ast_01", "1.0.0", "usr_01");
        when(versionRepository.findByCoordinate("ast_01", "1.0.0"))
                .thenReturn(Optional.of(existing));

        assertThatThrownBy(() -> service.createDraftVersion("ast_01", "1.0.0", "usr_01"))
                .isInstanceOf(ConflictException.class);
    }

    @Test
    void getVersionReturnsView() {
        Version v = Version.createDraft("ver_01", "ast_01", "v1.0.0", "usr_01");
        when(versionRepository.findByVersionId("ver_01")).thenReturn(Optional.of(v));

        VersionView view = service.getVersion("ver_01");

        assertThat(view.versionId()).isEqualTo("ver_01");
        verify(authorizationService).requirePermission(Permissions.ASSET_READ);
    }

    @Test
    void getVersionReturnsNotFoundForMissing() {
        when(versionRepository.findByVersionId("ver_missing"))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.getVersion("ver_missing"))
                .isInstanceOf(NotFoundException.class);
    }

    @Test
    void transitionVersionUpdatesStatus() {
        Version v = Version.createDraft("ver_01", "ast_01", "v1.0.0", "usr_01");
        when(versionRepository.findByVersionId("ver_01")).thenReturn(Optional.of(v));

        VersionView view = service.transitionVersion("ver_01", VersionStatus.VALIDATING, "usr_01");

        assertThat(view.status()).isEqualTo(VersionStatus.VALIDATING);
        verify(versionRepository).update(any(Version.class));
    }

    @Test
    void transitionVersionRejectsIllegalTransition() {
        Version v = Version.createDraft("ver_01", "ast_01", "v1.0.0", "usr_01");
        when(versionRepository.findByVersionId("ver_01")).thenReturn(Optional.of(v));

        assertThatThrownBy(() ->
                service.transitionVersion("ver_01", VersionStatus.PUBLISHED, "usr_01"))
                .isInstanceOf(ConflictException.class);
    }

    @Test
    void publishVersionFreezesDigestAndTag() {
        Version v = Version.createDraft("ver_01", "ast_01", "v1.0.0", "usr_01");
        v.transitionTo(VersionStatus.VALIDATING);
        v.transitionTo(VersionStatus.PENDING_REVIEW);
        when(versionRepository.findByVersionId("ver_01")).thenReturn(Optional.of(v));

        VersionView view = service.publishVersion("ver_01", "sha256abc", "v1.0.0", "usr_01");

        assertThat(view.status()).isEqualTo(VersionStatus.PUBLISHED);
        assertThat(view.manifestDigest()).isEqualTo("sha256abc");
        assertThat(view.gitTag()).isEqualTo("v1.0.0");
    }

    @Test
    void listVersionsRequiresReadPermission() {
        when(versionRepository.listByAsset(anyString(), any(), anyInt()))
                .thenReturn(new CursorPage<>(List.of(), null, false));

        service.listVersions("ast_01", null, 20);

        verify(authorizationService).requirePermission(Permissions.ASSET_READ);
    }
}
