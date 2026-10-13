/*
 * 功能: 文本工件内容服务端代理契约测试（TASK-P0BR-077 §4 切片矩阵）——
 *       文本白名单/扩展名回退/jsonl 归一化、415/413/404 语义；共享归属链防枚举行为
 *       由 PreviewApplicationServiceRenderTest（同实现）覆盖，此处仅断言入口一致性。
 * 时间: 2026-10-10
 * 作者: AxeXie
 */
package com.aihub.version.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.catchThrowableOfType;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.aihub.authorization.application.AuthorizationService;
import com.aihub.job.application.JobApplicationService;
import com.aihub.platform.observability.application.PlatformMetrics;
import com.aihub.shared.error.ConflictException;
import com.aihub.shared.error.ErrorCode;
import com.aihub.shared.error.NotFoundException;
import com.aihub.shared.error.ValidationException;
import com.aihub.transfer.domain.StoragePort;
import com.aihub.version.domain.Artifact;
import com.aihub.version.domain.PreviewQueryPort;
import com.aihub.version.domain.Version;
import com.aihub.version.domain.VersionRepository;
import com.aihub.version.domain.VersionStatus;
import com.aihub.version.infrastructure.PreviewProperties;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
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
@DisplayName("PreviewApplicationService 文本内容代理（TASK-P0BR-077）")
class PreviewApplicationServiceContentTest {

    private static final String BUCKET = "dvc-cache";
    private static final long MAX_TEXT_BYTES = 1024L * 1024;

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
                new PreviewProperties(100, 50, 1024L * 1024, 5L * 1024 * 1024, MAX_TEXT_BYTES,
                        null, 0, 0, 0L),
                platformMetricsProvider);
    }

    private static Version publishedVersion() {
        Version v = Version.createDraft("ver_01", "ast_01", "0.1.0", "usr_owner");
        v.transitionTo(VersionStatus.VALIDATING);
        v.bindManifestDigest("sha256-abc");
        v.bindSourceCommit("a".repeat(40));
        v.transitionTo(VersionStatus.PENDING_REVIEW);
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

    private void stubObject(String key, byte[] bytes) {
        when(storagePort.objectExists(BUCKET, key)).thenReturn(true);
        when(storagePort.readObject(BUCKET, key)).thenReturn(new ByteArrayInputStream(bytes));
    }

    @ParameterizedTest(name = "文本白名单 {0} → 200 原样字节与 mediaType")
    @ValueSource(strings = {"text/plain", "text/csv", "text/markdown", "application/json",
            "application/x-ndjson"})
    void whitelistedTextTypesReturnBytes(String mediaType) {
        byte[] body = "col_a,col_b\n1,2\n".getBytes(StandardCharsets.UTF_8);
        stubHappyPathArtifacts(artifact("art_01", "data/train/part.csv", body.length, mediaType));
        String key = "ab/12cd34ef567890";
        stubObject(key, body);

        var result = service.getArtifactContent("ast_01", "ver_01", "art_01");

        assertThat(result.content()).isEqualTo(body);
        assertThat(result.mediaType()).isEqualTo(mediaType);
    }

    @Test
    @DisplayName("application/jsonl 接受并归一化为 application/x-ndjson 响应")
    void jsonlIsNormalizedToNdjson() {
        byte[] body = "{\"a\":1}\n{\"a\":2}\n".getBytes(StandardCharsets.UTF_8);
        stubHappyPathArtifacts(artifact("art_01", "data/train/x.jsonl", body.length, "application/jsonl"));
        stubObject("ab/12cd34ef567890", body);

        var result = service.getArtifactContent("ast_01", "ver_01", "art_01");

        assertThat(result.mediaType()).isEqualTo("application/x-ndjson");
        assertThat(result.content()).isEqualTo(body);
    }

    @ParameterizedTest(name = "扩展名回退 {0} → {1}")
    @ValueSource(strings = {"notes/README.md|text/markdown", "notes/TODO.txt|text/plain",
            "data/train/part.csv|text/csv", "meta/index.json|application/json",
            "data/train/x.ndjson|application/x-ndjson"})
    void fallsBackToPathSuffixWhenMediaTypeMissing(String caseSpec) {
        String[] parts = caseSpec.split("\\|");
        byte[] body = "hello".getBytes(StandardCharsets.UTF_8);
        stubHappyPathArtifacts(artifact("art_01", parts[0], body.length, null));
        stubObject("ab/12cd34ef567890", body);

        var result = service.getArtifactContent("ast_01", "ver_01", "art_01");

        assertThat(result.mediaType()).isEqualTo(parts[1]);
    }

    @ParameterizedTest(name = "白名单外类型 {0} → 415 且不触达存储")
    @ValueSource(strings = {"text/html", "image/png", "application/octet-stream", "application/parquet"})
    void nonWhitelistedMediaTypeIsRejectedWith415(String mediaType) {
        stubHappyPathArtifacts(artifact("art_01", "files/x.bin", 10, mediaType));

        assertThatThrownBy(() -> service.getArtifactContent("ast_01", "ver_01", "art_01"))
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

        assertThatThrownBy(() -> service.getArtifactContent("ast_01", "ver_01", "art_01"))
                .isInstanceOf(ValidationException.class)
                .extracting(e -> ((ValidationException) e).errorCode())
                .isEqualTo(ErrorCode.PREVIEW_UNSUPPORTED_FORMAT);

        verifyNoInteractions(storagePort);
    }

    @Test
    @DisplayName("413 预检：元数据 size 超过 maxTextBytes 直接拒绝，不发起存储调用")
    void sizePrecheckRejectsBeforeStorage() {
        stubHappyPathArtifacts(artifact("art_01", "data/big.csv", MAX_TEXT_BYTES + 1, "text/csv"));

        assertThatThrownBy(() -> service.getArtifactContent("ast_01", "ver_01", "art_01"))
                .isInstanceOf(ValidationException.class)
                .extracting(e -> ((ValidationException) e).errorCode())
                .isEqualTo(ErrorCode.PREVIEW_TOO_LARGE);

        verify(platformMetrics).recordPreviewFailure("limit_exceeded");
        verifyNoInteractions(storagePort);
    }

    @Test
    @DisplayName("跨绑定 artifactId 与未知 artifactId 同一 404（防枚举，共享渲染链）")
    void crossBindingAndUnknownArtifactShareNotFoundSemantics() {
        stubHappyPathArtifacts(artifact("art_OTHER", "data/train/part.csv", 5, "text/csv"));

        NotFoundException cross = catchThrowableOfType(
                () -> service.getArtifactContent("ast_01", "ver_01", "art_01"),
                NotFoundException.class);
        assertThat(cross.errorCode()).isEqualTo(ErrorCode.PREVIEW_NOT_FOUND);

        when(versionRepository.listArtifactsByVersion("ver_01")).thenReturn(List.of());
        NotFoundException unknown = catchThrowableOfType(
                () -> service.getArtifactContent("ast_01", "ver_01", "art_01"),
                NotFoundException.class);
        assertThat(unknown.errorCode()).isEqualTo(cross.errorCode());

        verifyNoInteractions(storagePort);
    }

    @Test
    @DisplayName("非 PUBLISHED 版本 → 409，且不触达存储")
    void draftVersionIsRejectedWithConflict() {
        when(versionRepository.findByVersionId("ver_01"))
                .thenReturn(Optional.of(Version.createDraft("ver_01", "ast_01", "0.1.0", "usr_owner")));

        assertThatThrownBy(() -> service.getArtifactContent("ast_01", "ver_01", "art_01"))
                .isInstanceOf(ConflictException.class)
                .extracting(e -> ((ConflictException) e).errorCode())
                .isEqualTo(ErrorCode.VERSION_STATE_NOT_ALLOWED);

        verifyNoInteractions(storagePort);
    }

    @Test
    @DisplayName("对象缺失 → 404 PREVIEW_SOURCE_UNAVAILABLE，不伪造空内容")
    void missingObjectFailsWithoutFakeContent() {
        stubHappyPathArtifacts(artifact("art_01", "data/train/part.csv", 5, "text/csv"));
        when(storagePort.objectExists(anyString(), anyString())).thenReturn(false);

        assertThatThrownBy(() -> service.getArtifactContent("ast_01", "ver_01", "art_01"))
                .isInstanceOf(NotFoundException.class)
                .extracting(e -> ((NotFoundException) e).errorCode())
                .isEqualTo(ErrorCode.PREVIEW_SOURCE_UNAVAILABLE);

        verify(storagePort, never()).readObject(anyString(), anyString());
    }
}
