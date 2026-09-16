/*
 * 功能: UploadMaterializeJobHandler 单元测试——覆盖路径校验、幂等、物化流程、SHA-256 一致性校验。
 * 时间: 2026-07-30
 * 作者: AxeXie
 */
package com.aihub.transfer.infrastructure;

import com.aihub.job.domain.JobContext;
import com.aihub.shared.id.IdGenerator;
import com.aihub.shared.id.IdPrefix;
import com.aihub.transfer.domain.StoragePort;
import com.aihub.transfer.domain.UploadSession;
import com.aihub.transfer.domain.UploadSessionRepository;
import com.aihub.transfer.domain.UploadSessionStatus;
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
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("UploadMaterializeJobHandler")
class UploadMaterializeJobHandlerTest {

    @Mock
    private UploadSessionRepository sessionRepository;
    @Mock
    private VersionRepository versionRepository;
    @Mock
    private IdGenerator idGenerator;
    @Mock
    private StoragePort storagePort;

    private UploadMaterializeJobHandler handler;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @BeforeEach
    void setUp() {
        handler = new UploadMaterializeJobHandler(sessionRepository, versionRepository, idGenerator, objectMapper, storagePort);
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
            UploadSession session = createOpenSession();
            when(sessionRepository.findBySessionId(any())).thenReturn(Optional.of(session));
            when(versionRepository.findByVersionId(any())).thenReturn(Optional.empty());
            when(idGenerator.generate(IdPrefix.ARTIFACT)).thenReturn("art_1");
            when(storagePort.statObject(anyString(), anyString()))
                    .thenReturn(new StoragePort.ObjectMetadata(100L, "abc123", "\"abc123\""));
            doNothing().when(storagePort).copyObject(anyString(), anyString(), anyString(), anyString());

            String payload = buildPayload("model.bin", "abc123", 100L);
            JobContext context = createContext(payload);

            handler.handle(context);
            // 不抛异常即通过
            verify(sessionRepository).update(any());
        }

        @Test
        @DisplayName("路径穿越应被拒绝")
        void pathTraversalShouldBeRejected() throws Exception {
            UploadSession session = createOpenSession();
            when(sessionRepository.findBySessionId(any())).thenReturn(Optional.of(session));

            String payload = buildPayload("../etc/passwd", "abc123", 100L);
            JobContext context = createContext(payload);

            assertThatThrownBy(() -> handler.handle(context))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("unsafe file path");
        }

        @Test
        @DisplayName("绝对路径应被拒绝")
        void absolutePathsShouldBeRejected() throws Exception {
            UploadSession session = createOpenSession();
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
            UploadSession session = createOpenSession();
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
            UploadSession session = createOpenSession();
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
            UploadSession session = createOpenSession();
            session.commit();
            session.complete();
            when(sessionRepository.findBySessionId(any())).thenReturn(Optional.of(session));

            String payload = buildPayload("model.bin", "abc123", 100L);
            JobContext context = createContext(payload);

            handler.handle(context);

            // 不应再次更新会话
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
            UploadSession session = createOpenSession();
            when(sessionRepository.findBySessionId(any())).thenReturn(Optional.of(session));

            Version version = Version.createDraft("ver_1", "asset_1", "v1", "principal_1");
            when(versionRepository.findByVersionId(any())).thenReturn(Optional.of(version));
            when(idGenerator.generate(IdPrefix.ARTIFACT)).thenReturn("art_1");
            when(storagePort.statObject(anyString(), anyString()))
                    .thenReturn(new StoragePort.ObjectMetadata(1024L, "sha256abc", "\"sha256abc\""));
            doNothing().when(storagePort).copyObject(anyString(), anyString(), anyString(), anyString());

            String payload = buildPayload("model.bin", "sha256abc", 1024L);
            JobContext context = createContext(payload);

            handler.handle(context);

            // 验证工件写入
            verify(versionRepository).deleteArtifactsByVersion("ver_1");
            verify(versionRepository).insertArtifact(any(Artifact.class));

            // 验证版本状态推进
            assertThat(version.status()).isEqualTo(VersionStatus.VALIDATING);
            assertThat(version.manifestDigest()).isNotNull();
            verify(versionRepository).update(version);

            // 验证会话完成
            verify(sessionRepository).update(any(UploadSession.class));
        }

        @Test
        @DisplayName("工件删除后再插入应保证幂等")
        void shouldDeleteBeforeInsert() throws Exception {
            UploadSession session = createOpenSession();
            when(sessionRepository.findBySessionId(any())).thenReturn(Optional.of(session));
            when(versionRepository.findByVersionId(any())).thenReturn(Optional.empty());
            when(idGenerator.generate(IdPrefix.ARTIFACT)).thenReturn("art_1", "art_2");
            when(storagePort.statObject(anyString(), anyString()))
                    .thenReturn(new StoragePort.ObjectMetadata(100L, "abc", "\"abc\""))
                    .thenReturn(new StoragePort.ObjectMetadata(50L, "def", "\"def\""));
            doNothing().when(storagePort).copyObject(anyString(), anyString(), anyString(), anyString());

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

            // 验证先删除后插入
            verify(versionRepository).deleteArtifactsByVersion("ver_1");
            verify(versionRepository, times(2)).insertArtifact(any(Artifact.class));
        }
    }

    @Nested
    @DisplayName("SHA-256 一致性校验 (REQ-DVC-001)")
    class Sha256ConsistencyCheck {

        @Test
        @DisplayName("客户端 SHA-256 与存储对象 SHA-256 一致时应通过")
        void shouldPassWhenSha256Matches() throws Exception {
            UploadSession session = createOpenSession();
            when(sessionRepository.findBySessionId(any())).thenReturn(Optional.of(session));
            when(versionRepository.findByVersionId(any())).thenReturn(Optional.empty());
            when(idGenerator.generate(IdPrefix.ARTIFACT)).thenReturn("art_1");

            // 模拟 MinIO 对象 ETag（非 multipart = SHA-256）
            String expectedSha256 = "abcdef1234567890abcdef1234567890abcdef1234567890abcdef1234567890";
            when(storagePort.statObject(anyString(), anyString()))
                    .thenReturn(new StoragePort.ObjectMetadata(100L, expectedSha256, "\"" + expectedSha256 + "\""));
            doNothing().when(storagePort).copyObject(anyString(), anyString(), anyString(), anyString());

            String payload = buildPayload("model.bin", expectedSha256, 100L);
            JobContext context = createContext(payload);

            // 不应抛出异常
            handler.handle(context);

            // 验证 DVC push 被调用
            verify(storagePort).copyObject(eq("asset-staging"), anyString(), eq("dvc-cache"), anyString());
            // 验证工件写入
            verify(versionRepository).insertArtifact(any(Artifact.class));
        }

        @Test
        @DisplayName("SHA-256 不一致时应拒绝物化")
        void shouldFailWhenSha256Mismatch() throws Exception {
            UploadSession session = createOpenSession();
            when(sessionRepository.findBySessionId(any())).thenReturn(Optional.of(session));

            String clientSha256 = "client1234567890abcdef1234567890abcdef1234567890abcdef12345678";
            String actualSha256 = "actual1234567890abcdef1234567890abcdef1234567890abcdef12345678";
            when(storagePort.statObject(anyString(), anyString()))
                    .thenReturn(new StoragePort.ObjectMetadata(100L, actualSha256, "\"" + actualSha256 + "\""));

            String payload = buildPayload("model.bin", clientSha256, 100L);
            JobContext context = createContext(payload);

            assertThatThrownBy(() -> handler.handle(context))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("SHA-256 digest mismatch")
                    .hasMessageContaining(clientSha256)
                    .hasMessageContaining(actualSha256);

            // DVC push 不应被调用
            verify(storagePort, never()).copyObject(anyString(), anyString(), anyString(), anyString());
            // 工件不应被写入
            verify(versionRepository, never()).insertArtifact(any(Artifact.class));
        }

        @Test
        @DisplayName("SHA-256 大小写不敏感比对")
        void shouldIgnoreCaseWhenComparingSha256() throws Exception {
            UploadSession session = createOpenSession();
            when(sessionRepository.findBySessionId(any())).thenReturn(Optional.of(session));
            when(versionRepository.findByVersionId(any())).thenReturn(Optional.empty());
            when(idGenerator.generate(IdPrefix.ARTIFACT)).thenReturn("art_1");

            // 客户端提供大写 hex，MinIO 返回小写 hex
            String upperSha256 = "ABCDEF1234567890ABCDEF1234567890ABCDEF1234567890ABCDEF1234567890";
            String lowerSha256 = "abcdef1234567890abcdef1234567890abcdef1234567890abcdef1234567890";
            when(storagePort.statObject(anyString(), anyString()))
                    .thenReturn(new StoragePort.ObjectMetadata(100L, lowerSha256, "\"" + lowerSha256 + "\""));
            doNothing().when(storagePort).copyObject(anyString(), anyString(), anyString(), anyString());

            String payload = buildPayload("model.bin", upperSha256, 100L);
            JobContext context = createContext(payload);

            // 不应抛出异常：大小写差异被忽略
            handler.handle(context);

            verify(storagePort).copyObject(anyString(), anyString(), anyString(), anyString());
        }
    }

    private UploadSession createOpenSession() {
        Instant now = Instant.now();
        return new UploadSession("sess_1", "asset_1", "v1", "principal_1",
                UploadSessionStatus.OPEN, 100L, 1, now.plusSeconds(7200),
                null, now, now);
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
