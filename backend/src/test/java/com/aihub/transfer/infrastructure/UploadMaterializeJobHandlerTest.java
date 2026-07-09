package com.aihub.transfer.infrastructure;

import com.aihub.asset.domain.Asset;
import com.aihub.asset.domain.AssetRepository;
import com.aihub.asset.domain.AssetType;
import com.aihub.asset.domain.Visibility;
import com.aihub.job.domain.JobContext;
import com.aihub.shared.id.IdGenerator;
import com.aihub.shared.id.IdPrefix;
import com.aihub.transfer.domain.UploadSession;
import com.aihub.transfer.domain.UploadSessionRepository;
import com.aihub.transfer.domain.UploadSessionStatus;
import com.aihub.version.application.PreviewApplicationService;
import com.aihub.version.domain.Artifact;
import com.aihub.version.domain.Version;
import com.aihub.version.domain.VersionRepository;
import com.aihub.version.domain.VersionStatus;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("UploadMaterializeJobHandler")
class UploadMaterializeJobHandlerTest {

    @Mock
    private UploadSessionRepository sessionRepository;
    @Mock
    private VersionRepository versionRepository;
    @Mock
    private AssetRepository assetRepository;
    @Mock
    private IdGenerator idGenerator;
    @Mock
    private PreviewApplicationService previewApplicationService;

    private UploadMaterializeJobHandler handler;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @BeforeEach
    void setUp() {
        handler = new UploadMaterializeJobHandler(sessionRepository, versionRepository,
                assetRepository, idGenerator, objectMapper, previewApplicationService);
    }

    @Test
    @DisplayName("应返回正确的任务类型")
    void shouldReturnCorrectType() {
        assertThat(handler.type()).isEqualTo("UPLOAD_MATERIALIZE");
    }

    @Nested
    @DisplayName("路径安全校验")
    class PathSafetyValidation {

        @Test
        @DisplayName("合法路径应通过校验")
        void validPathsShouldPass() throws Exception {
            UploadSession session = createProcessingSession();
            when(sessionRepository.findBySessionId(any())).thenReturn(Optional.of(session));
            when(versionRepository.findByVersionId(any())).thenReturn(Optional.empty());
            when(idGenerator.generate(IdPrefix.ARTIFACT)).thenReturn("art_1");

            String payload = buildPayload("model.bin", "abc123", 100L);
            JobContext context = createContext(payload);

            handler.handle(context);
            verify(sessionRepository).update(any());
            assertThat(session.status()).isEqualTo(UploadSessionStatus.COMPLETED);
        }

        @Test
        @DisplayName("路径穿越应被拒绝")
        void pathTraversalShouldBeRejected() throws Exception {
            UploadSession session = createProcessingSession();
            when(sessionRepository.findBySessionId(any())).thenReturn(Optional.of(session));

            String payload = buildPayload("../etc/passwd", "abc123", 100L);
            JobContext context = createContext(payload);

            assertThatThrownBy(() -> handler.handle(context))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("unsafe file path");
            assertThat(session.status()).isEqualTo(UploadSessionStatus.FAILED);
        }

        @Test
        @DisplayName("绝对路径应被拒绝")
        void absolutePathsShouldBeRejected() throws Exception {
            UploadSession session = createProcessingSession();
            when(sessionRepository.findBySessionId(any())).thenReturn(Optional.of(session));

            String payload = buildPayload("/etc/passwd", "abc123", 100L);
            JobContext context = createContext(payload);

            assertThatThrownBy(() -> handler.handle(context))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("unsafe file path");
        }

        @Test
        @DisplayName("空字节应被拒绝")
        void nullByteShouldBeRejected() throws Exception {
            UploadSession session = createProcessingSession();
            when(sessionRepository.findBySessionId(any())).thenReturn(Optional.of(session));

            String payload = buildPayload("model\0.bin", "abc123", 100L);
            JobContext context = createContext(payload);

            assertThatThrownBy(() -> handler.handle(context))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("null byte");
        }

        @Test
        @DisplayName("疑似符号链接应被拒绝")
        void symlinkShouldBeRejected() throws Exception {
            UploadSession session = createProcessingSession();
            when(sessionRepository.findBySessionId(any())).thenReturn(Optional.of(session));

            String payload = buildPayload("link->target", "abc123", 100L);
            JobContext context = createContext(payload);

            assertThatThrownBy(() -> handler.handle(context))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("symlink");
        }
    }

    @Nested
    @DisplayName("幂等性保证")
    class Idempotency {

        @Test
        @DisplayName("会话已完成时应跳过执行")
        void shouldSkipWhenSessionCompleted() throws Exception {
            UploadSession session = createProcessingSession();
            session.complete();
            when(sessionRepository.findBySessionId(any())).thenReturn(Optional.of(session));

            String payload = buildPayload("model.bin", "abc123", 100L);
            JobContext context = createContext(payload);

            handler.handle(context);

            verify(sessionRepository, never()).update(any());
        }

        @Test
        @DisplayName("会话不存在时应跳过执行")
        void shouldSkipWhenSessionNotFound() throws Exception {
            when(sessionRepository.findBySessionId(any())).thenReturn(Optional.empty());

            String payload = buildPayload("model.bin", "abc123", 100L);
            JobContext context = createContext(payload);

            handler.handle(context);

            verify(versionRepository, never()).insertArtifact(any());
        }
    }

    @Nested
    @DisplayName("物化流程")
    class MaterializationFlow {

        @Test
        @DisplayName("应生成工件并推进版本状态")
        void shouldGenerateArtifactsAndAdvanceVersion() throws Exception {
            UploadSession session = createProcessingSession();
            when(sessionRepository.findBySessionId(any())).thenReturn(Optional.of(session));

            Version version = Version.createDraft("ver_1", "asset_1", "v1", "principal_1");
            when(versionRepository.findByVersionId(any())).thenReturn(Optional.of(version));
            when(idGenerator.generate(IdPrefix.ARTIFACT)).thenReturn("art_1");

            String payload = buildPayload("model.bin", "sha256abc", 1024L);
            JobContext context = createContext(payload);

            handler.handle(context);

            verify(versionRepository).deleteArtifactsByVersion("ver_1");
            verify(versionRepository).insertArtifact(any(Artifact.class));

            assertThat(version.status()).isEqualTo(VersionStatus.VALIDATING);
            assertThat(version.manifestDigest()).isNotNull();
            verify(versionRepository).update(version);

            verify(sessionRepository).update(any(UploadSession.class));
            assertThat(session.status()).isEqualTo(UploadSessionStatus.COMPLETED);
        }

        @Test
        @DisplayName("PROCESSING 会话应物化到 COMPLETED")
        void shouldProcessProcessingSession() throws Exception {
            UploadSession session = createProcessingSession();
            when(sessionRepository.findBySessionId(any())).thenReturn(Optional.of(session));
            when(versionRepository.findByVersionId(any())).thenReturn(Optional.empty());
            when(idGenerator.generate(IdPrefix.ARTIFACT)).thenReturn("art_1");

            handler.handle(createContext(buildPayload("data.csv", "abc", 10L)));

            assertThat(session.status()).isEqualTo(UploadSessionStatus.COMPLETED);
        }

        @Test
        @DisplayName("数据集样本内容应触发预览入队")
        void shouldEnqueuePreviewForDatasetWithSample() throws Exception {
            UploadSession session = createProcessingSession();
            when(sessionRepository.findBySessionId(any())).thenReturn(Optional.of(session));
            when(versionRepository.findByVersionId(any())).thenReturn(Optional.empty());
            when(idGenerator.generate(IdPrefix.ARTIFACT)).thenReturn("art_1");
            Asset dataset = Asset.create("asset_1", AssetType.DATASET,
                    "org_1", null, "testorg", "ds",
                    "Dataset", "desc", Visibility.PUBLIC,
                    List.of("creator_1"), List.of(), List.of(), null,
                    null, null, null, "creator_1");
            when(assetRepository.findByAssetId("asset_1")).thenReturn(Optional.of(dataset));
            when(previewApplicationService.enqueuePreview(anyString(), anyString(), anyString(),
                    anyString(), any())).thenReturn("job_prev_1");

            Map<String, Object> payloadMap = new HashMap<>();
            payloadMap.put("sessionId", "sess_1");
            payloadMap.put("assetId", "asset_1");
            payloadMap.put("versionId", "ver_1");
            payloadMap.put("files", List.of(Map.of(
                    "path", "data.csv", "sha256", "abc", "size", 10,
                    "mediaType", "text/csv", "sampleContent", "a,b\n1,2")));
            JobContext context = createContext(objectMapper.writeValueAsString(payloadMap));

            handler.handle(context);

            verify(previewApplicationService).enqueuePreview(
                    eq("asset_1"), eq("ver_1"), anyString(), eq("text/csv"), eq("principal_1"));
        }

        @Test
        @DisplayName("工件删除后再插入应保证幂等")
        void shouldDeleteBeforeInsert() throws Exception {
            UploadSession session = createProcessingSession();
            when(sessionRepository.findBySessionId(any())).thenReturn(Optional.of(session));
            when(versionRepository.findByVersionId(any())).thenReturn(Optional.empty());
            when(idGenerator.generate(IdPrefix.ARTIFACT)).thenReturn("art_1", "art_2");

            Map<String, Object> payloadMap = new HashMap<>();
            payloadMap.put("sessionId", "sess_1");
            payloadMap.put("assetId", "asset_1");
            payloadMap.put("versionId", "ver_1");
            payloadMap.put("files", List.of(
                    Map.of("path", "model.bin", "sha256", "abc", "size", 100),
                    Map.of("path", "config.json", "sha256", "def", "size", 50)
            ));
            String payload = objectMapper.writeValueAsString(payloadMap);
            JobContext context = createContext(payload);

            handler.handle(context);

            verify(versionRepository).deleteArtifactsByVersion("ver_1");
            verify(versionRepository, times(2)).insertArtifact(any(Artifact.class));
        }
    }

    private UploadSession createProcessingSession() {
        Instant now = Instant.now();
        UploadSession session = new UploadSession("sess_1", "asset_1", "ver_1", "principal_1",
                UploadSessionStatus.OPEN, 100L, 1, now.plusSeconds(7200),
                null, now, now);
        session.commit();
        session.markProcessing();
        return session;
    }

    private String buildPayload(String path, String sha256, long size) throws Exception {
        Map<String, Object> payload = new HashMap<>();
        payload.put("sessionId", "sess_1");
        payload.put("assetId", "asset_1");
        payload.put("versionId", "ver_1");
        payload.put("files", List.of(Map.of("path", path, "sha256", sha256, "size", size)));
        return objectMapper.writeValueAsString(payload);
    }

    private JobContext createContext(String payload) {
        return new JobContext("job_1", "UPLOAD_MATERIALIZE", payload, 0, "principal_1", "trace_1", "asset_1");
    }
}
