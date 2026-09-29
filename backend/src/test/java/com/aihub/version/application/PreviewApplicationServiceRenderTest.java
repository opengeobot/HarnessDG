/*
 * 功能: 图片预览服务端代理渲染契约测试（TASK-P0BR-068 §4 切片矩阵）——
 *       权限先于存储、跨绑定同 404 防枚举、409/415/413/404 语义与流式双保险。
 * 时间: 2026-09-29
 * 作者: AxeXie
 */
package com.aihub.version.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.catchThrowableOfType;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.aihub.authorization.application.AuthorizationService;
import com.aihub.job.application.JobApplicationService;
import com.aihub.platform.observability.application.PlatformMetrics;
import com.aihub.shared.error.AuthorizationException;
import com.aihub.shared.error.ConflictException;
import com.aihub.shared.error.ErrorCode;
import com.aihub.shared.error.NotFoundException;
import com.aihub.shared.error.ValidationException;
import com.aihub.transfer.domain.StoragePort;
import com.aihub.version.domain.Artifact;
import com.aihub.version.domain.PreviewQueryPort;
import com.aihub.version.domain.Version;
import com.aihub.version.domain.VersionRepository;
import com.aihub.version.infrastructure.PreviewProperties;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.beans.factory.ObjectProvider;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("PreviewApplicationService 图片渲染（TASK-P0BR-068）")
class PreviewApplicationServiceRenderTest {

    private static final String BUCKET = "dvc-cache";
    private static final long MAX_RENDER_BYTES = 5L * 1024 * 1024;

    @Mock private JobApplicationService jobApplicationService;
    @Mock private PreviewQueryPort previewQueryPort;
    @Mock private AuthorizationService authorizationService;
    @Mock private VersionRepository versionRepository;
    @Mock private StoragePort storagePort;
    @Mock private PlatformMetrics platformMetrics;
    @Mock private ObjectProvider<PlatformMetrics> platformMetricsProvider;

    private PreviewApplicationService service;

    @BeforeEach
    void setUp() {
        when(platformMetricsProvider.getIfAvailable()).thenReturn(platformMetrics);
        service = new PreviewApplicationService(jobApplicationService, new ObjectMapper(),
                previewQueryPort, authorizationService, versionRepository, storagePort,
                new PreviewProperties(100, 50, 1024L * 1024, MAX_RENDER_BYTES),
                platformMetricsProvider);
    }

    private static Version publishedVersion() {
        Version v = Version.createDraft("ver_01", "ast_01", "0.1.0", "usr_owner");
        v.transitionTo(com.aihub.version.domain.VersionStatus.VALIDATING);
        v.bindManifestDigest("sha256-abc");
        v.bindSourceCommit("a".repeat(40));
        v.transitionTo(com.aihub.version.domain.VersionStatus.PENDING_REVIEW);
        v.publish("sha256-abc", "v0.1.0", "usr_admin");
        return v;
    }

    private static Artifact artifact(String artifactId, String path, long size, String mediaType) {
        return new Artifact(artifactId, "ver_01", path, path + ".dvc",
                "ab12cd34ef567890", "sha256-x", size, mediaType);
    }

    private void stubHappyPathArtifacts(Artifact... artifacts) {
        when(versionRepository.findByVersionId("ver_01")).thenReturn(Optional.of(publishedVersion()));
        when(versionRepository.listArtifactsByVersion("ver_01")).thenReturn(List.of(artifacts));
    }

    @Test
    @DisplayName("200：PNG 字节与存储对象一致，mediaType 为记录值")
    void rendersPngBytesWithRecordedMediaType() {
        byte[] png = new byte[] {(byte) 0x89, 'P', 'N', 'G', 1, 2, 3};
        stubHappyPathArtifacts(artifact("art_01", "images/cat.png", png.length, "image/png"));
        String key = "ab/12cd34ef567890";
        when(storagePort.objectExists(BUCKET, key)).thenReturn(true);
        when(storagePort.readObject(BUCKET, key)).thenReturn(new ByteArrayInputStream(png));

        var rendered = service.renderPreviewArtifact("ast_01", "ver_01", "art_01");

        assertThat(rendered.content()).isEqualTo(png);
        assertThat(rendered.mediaType()).isEqualTo("image/png");
    }

    @Test
    @DisplayName("mediaType 缺失时按扩展名回退推导")
    void fallsBackToPathSuffixWhenMediaTypeMissing() {
        byte[] jpeg = "fakejpegbytes".getBytes();
        stubHappyPathArtifacts(artifact("art_01", "images/photo.JPG", jpeg.length, null));
        String key = "ab/12cd34ef567890";
        when(storagePort.objectExists(BUCKET, key)).thenReturn(true);
        when(storagePort.readObject(BUCKET, key)).thenReturn(new ByteArrayInputStream(jpeg));

        var rendered = service.renderPreviewArtifact("ast_01", "ver_01", "art_01");

        assertThat(rendered.mediaType()).isEqualTo("image/jpeg");
        assertThat(rendered.content()).isEqualTo(jpeg);
    }

    @Test
    @DisplayName("403 先于一切存储与仓储访问（fail-closed）")
    void permissionCheckPrecedesAllLookupAndStorageAccess() {
        doThrow(new AuthorizationException(ErrorCode.AUTH_PERMISSION_DENIED,
                "missing required permission", Map.of()))
                .when(authorizationService).requirePermission("asset:read");

        assertThatThrownBy(() -> service.renderPreviewArtifact("ast_01", "ver_01", "art_01"))
                .isInstanceOf(AuthorizationException.class);

        verifyNoInteractions(versionRepository, storagePort);
    }

    @Test
    @DisplayName("版本属于其他资产 → 与未知版本同一 404（防枚举）")
    void crossAssetVersionReturnsSameNotFoundAsUnknownVersion() {
        when(versionRepository.findByVersionId("ver_01"))
                .thenReturn(Optional.of(Version.createDraft("ver_01", "ast_OTHER", "0.1.0", "usr_owner")));

        NotFoundException crossAsset = catchThrowableOfType(
                () -> service.renderPreviewArtifact("ast_01", "ver_01", "art_01"),
                NotFoundException.class);
        assertThat(crossAsset.errorCode()).isEqualTo(ErrorCode.PREVIEW_NOT_FOUND);

        when(versionRepository.findByVersionId("ver_01")).thenReturn(Optional.empty());
        NotFoundException unknown = catchThrowableOfType(
                () -> service.renderPreviewArtifact("ast_01", "ver_01", "art_01"),
                NotFoundException.class);

        // 防枚举：跨绑定与未知资源的错误码完全一致
        assertThat(unknown.errorCode()).isEqualTo(crossAsset.errorCode());
        verifyNoInteractions(storagePort);
    }

    @Test
    @DisplayName("artifact 不属于该版本 → 同一 404 语义")
    void artifactFromAnotherVersionReturnsNotFound() {
        stubHappyPathArtifacts(artifact("art_OTHER", "images/cat.png", 3, "image/png"));

        assertThatThrownBy(() -> service.renderPreviewArtifact("ast_01", "ver_01", "art_01"))
                .isInstanceOf(NotFoundException.class)
                .extracting(e -> ((NotFoundException) e).errorCode())
                .isEqualTo(ErrorCode.PREVIEW_NOT_FOUND);

        verifyNoInteractions(storagePort);
    }

    @Test
    @DisplayName("非 PUBLISHED 版本 → 409，且不触达存储")
    void draftVersionIsRejectedWithConflict() {
        when(versionRepository.findByVersionId("ver_01"))
                .thenReturn(Optional.of(Version.createDraft("ver_01", "ast_01", "0.1.0", "usr_owner")));

        assertThatThrownBy(() -> service.renderPreviewArtifact("ast_01", "ver_01", "art_01"))
                .isInstanceOf(ConflictException.class)
                .extracting(e -> ((ConflictException) e).errorCode())
                .isEqualTo(ErrorCode.VERSION_STATE_NOT_ALLOWED);

        verifyNoInteractions(storagePort);
    }

    @ParameterizedTest(name = "白名单外类型 {0} → 415")
    @ValueSource(strings = {"image/svg+xml", "text/csv", "application/octet-stream"})
    void nonWhitelistedMediaTypeIsRejectedWith415(String mediaType) {
        stubHappyPathArtifacts(artifact("art_01", "images/evil.img", 10, mediaType));

        assertThatThrownBy(() -> service.renderPreviewArtifact("ast_01", "ver_01", "art_01"))
                .isInstanceOf(ValidationException.class)
                .extracting(e -> ((ValidationException) e).errorCode())
                .isEqualTo(ErrorCode.PREVIEW_UNSUPPORTED_FORMAT);

        verify(platformMetrics).recordPreviewFailure("unsupported_format");
        verifyNoInteractions(storagePort);
    }

    @Test
    @DisplayName("mediaType 缺失且扩展名不可识别 → 415")
    void unknownExtensionWithoutMediaTypeIsRejectedWith415() {
        stubHappyPathArtifacts(artifact("art_01", "data/mystery.bin", 10, " "));

        assertThatThrownBy(() -> service.renderPreviewArtifact("ast_01", "ver_01", "art_01"))
                .isInstanceOf(ValidationException.class)
                .extracting(e -> ((ValidationException) e).errorCode())
                .isEqualTo(ErrorCode.PREVIEW_UNSUPPORTED_FORMAT);

        verifyNoInteractions(storagePort);
    }

    @Test
    @DisplayName("413 预检：元数据 size 超限直接拒绝，不发起任何存储调用")
    void sizePrecheckRejectsBeforeStorage() {
        stubHappyPathArtifacts(artifact("art_01", "images/big.png", MAX_RENDER_BYTES + 1, "image/png"));

        assertThatThrownBy(() -> service.renderPreviewArtifact("ast_01", "ver_01", "art_01"))
                .isInstanceOf(ValidationException.class)
                .extracting(e -> ((ValidationException) e).errorCode())
                .isEqualTo(ErrorCode.PREVIEW_TOO_LARGE);

        verify(platformMetrics).recordPreviewFailure("limit_exceeded");
        verifyNoInteractions(storagePort);
    }

    @Test
    @DisplayName("413 双保险：元数据 size 偏小但流式内容超限时仍拒绝")
    void streamingCapRejectsWhenActualBytesExceedLimit() {
        stubHappyPathArtifacts(artifact("art_01", "images/lie.png", 10, "image/png"));
        String key = "ab/12cd34ef567890";
        when(storagePort.objectExists(BUCKET, key)).thenReturn(true);
        when(storagePort.readObject(BUCKET, key)).thenReturn(new InputStream() {
            private long sent;

            @Override
            public int read() throws IOException {
                if (sent >= MAX_RENDER_BYTES + 1) {
                    return -1;
                }
                sent++;
                return 65;
            }

            @Override
            public int read(byte[] b, int off, int len) {
                int n = (int) Math.min(len, MAX_RENDER_BYTES + 1 - sent);
                if (n <= 0) {
                    return -1;
                }
                java.util.Arrays.fill(b, off, off + n, (byte) 65);
                sent += n;
                return n;
            }
        });

        assertThatThrownBy(() -> service.renderPreviewArtifact("ast_01", "ver_01", "art_01"))
                .isInstanceOf(ValidationException.class)
                .extracting(e -> ((ValidationException) e).errorCode())
                .isEqualTo(ErrorCode.PREVIEW_TOO_LARGE);

        verify(platformMetrics).recordPreviewFailure("limit_exceeded");
    }

    @Test
    @DisplayName("对象缺失 → 404 PREVIEW_SOURCE_UNAVAILABLE，不伪造空图、不读流")
    void missingObjectFailsWithoutReadingStream() {
        stubHappyPathArtifacts(artifact("art_01", "images/cat.png", 5, "image/png"));
        when(storagePort.objectExists(eq(BUCKET), anyString())).thenReturn(false);

        assertThatThrownBy(() -> service.renderPreviewArtifact("ast_01", "ver_01", "art_01"))
                .isInstanceOf(NotFoundException.class)
                .extracting(e -> ((NotFoundException) e).errorCode())
                .isEqualTo(ErrorCode.PREVIEW_SOURCE_UNAVAILABLE);

        verify(storagePort, never()).readObject(anyString(), anyString());
        verify(platformMetrics).recordPreviewFailure("source_unavailable");
    }

    @Test
    @DisplayName("读取 IO 失败 → 404 PREVIEW_SOURCE_UNAVAILABLE（不泄露内部错误）")
    void ioFailureMapsToSourceUnavailable() {
        stubHappyPathArtifacts(artifact("art_01", "images/cat.png", 5, "image/png"));
        String key = "ab/12cd34ef567890";
        when(storagePort.objectExists(BUCKET, key)).thenReturn(true);
        when(storagePort.readObject(BUCKET, key)).thenReturn(new InputStream() {
            @Override
            public int read() throws IOException {
                throw new IOException("minio internal endpoint unreachable");
            }
        });

        assertThatThrownBy(() -> service.renderPreviewArtifact("ast_01", "ver_01", "art_01"))
                .isInstanceOf(NotFoundException.class)
                .hasMessageNotContaining("minio")
                .extracting(e -> ((NotFoundException) e).errorCode())
                .isEqualTo(ErrorCode.PREVIEW_SOURCE_UNAVAILABLE);

        verify(platformMetrics).recordPreviewFailure("source_read_failed");
    }
}
