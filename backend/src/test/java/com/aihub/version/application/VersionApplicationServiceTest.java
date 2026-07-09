package com.aihub.version.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.aihub.audit.application.AuditService;
import com.aihub.authorization.application.AuthorizationService;
import com.aihub.authorization.domain.Permissions;
import com.aihub.shared.api.CursorPage;
import com.aihub.shared.error.ConflictException;
import com.aihub.shared.error.NotFoundException;
import com.aihub.shared.id.IdGenerator;
import com.aihub.shared.id.IdPrefix;
import com.aihub.version.domain.Version;
import com.aihub.version.domain.VersionRepository;
import org.springframework.jdbc.core.JdbcTemplate;
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
    @Mock private AuthorizationService authorizationService;
    @Mock private AuditService auditService;
    @Mock private IdGenerator idGenerator;
    @Mock private JdbcTemplate jdbcTemplate;
    @Mock private com.aihub.notification.application.NotificationService notificationService;

    private VersionApplicationService service;

    @BeforeEach
    void setUp() {
        service = new VersionApplicationService(
                versionRepository, authorizationService, auditService, idGenerator, jdbcTemplate,
                notificationService);
        when(idGenerator.generate(any(IdPrefix.class))).thenReturn("ver_generated");
    }

    @Test
    void createDraftVersionInsertsAndAudits() {
        VersionView view = service.createDraftVersion("ast_01", "v1.0.0", "usr_01");

        assertThat(view.versionId()).isEqualTo("ver_generated");
        assertThat(view.status()).isEqualTo(VersionStatus.DRAFT);
        verify(authorizationService).requirePermission(Permissions.ASSET_MANAGE);
        verify(versionRepository).insert(any(Version.class));
        verify(auditService).record(any());
    }

    @Test
    void createDraftVersionRejectsDuplicateCoordinate() {
        Version existing = Version.createDraft("ver_existing", "ast_01", "v1.0.0", "usr_01");
        when(versionRepository.findByCoordinate("ast_01", "v1.0.0"))
                .thenReturn(Optional.of(existing));

        assertThatThrownBy(() -> service.createDraftVersion("ast_01", "v1.0.0", "usr_01"))
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
