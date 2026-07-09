package com.aihub.version.infrastructure;

import com.aihub.job.domain.JobContext;
import com.aihub.shared.id.IdGenerator;
import com.aihub.shared.id.IdPrefix;
import com.aihub.version.domain.Artifact;
import com.aihub.version.domain.Version;
import com.aihub.version.domain.VersionRepository;
import com.aihub.version.domain.VersionStatus;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.jdbc.core.JdbcTemplate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("ValidationJobHandler")
class ValidationJobHandlerTest {

    @Mock
    private VersionRepository versionRepository;
    @Mock
    private JdbcTemplate jdbcTemplate;
    @Mock
    private IdGenerator idGenerator;

    private ValidationJobHandler handler;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @BeforeEach
    void setUp() {
        handler = new ValidationJobHandler(versionRepository, jdbcTemplate, idGenerator, objectMapper);
    }

    @Test
    @DisplayName("应返回正确的任务类型")
    void shouldReturnCorrectType() {
        assertThat(handler.type()).isEqualTo("VERSION_VALIDATE");
    }

    @Test
    @DisplayName("版本未找到时应静默跳过")
    void shouldSkipWhenVersionNotFound() throws Exception {
        when(versionRepository.findByVersionId("ver_missing")).thenReturn(Optional.empty());

        JobContext context = new JobContext("job_1", "VERSION_VALIDATE",
                "{\"versionId\":\"ver_missing\"}", 0, "principal_1", "trace_1", "ast_1");

        handler.handle(context);

        verify(versionRepository, never()).update(any());
        verify(jdbcTemplate, never()).update(anyString(), any(), any(), any(), any(), any(), any());
    }

    @Test
    @DisplayName("非 VALIDATING 状态应跳过")
    void shouldSkipWhenNotValidating() throws Exception {
        Version version = Version.createDraft("ver_1", "ast_1", "v1.0.0", "user_1");
        when(versionRepository.findByVersionId("ver_1")).thenReturn(Optional.of(version));

        JobContext context = new JobContext("job_1", "VERSION_VALIDATE",
                "{\"versionId\":\"ver_1\"}", 0, "principal_1", "trace_1", "ast_1");

        handler.handle(context);

        verify(versionRepository, never()).update(any());
    }

    @Test
    @DisplayName("全部校验通过 → PENDING_REVIEW + 写入报告")
    void shouldTransitionToPendingReviewWhenAllPassed() throws Exception {
        Version version = Version.createDraft("ver_1", "ast_1", "v1.0.0", "user_1");
        version.transitionTo(VersionStatus.VALIDATING);
        version.bindManifestDigest("sha256:abc123");

        Artifact artifact = new Artifact("art_1", "ver_1", "model.bin",
                "model.dvc", "dvc:xyz", "sha256:hash", 1024, "application/octet-stream");

        when(versionRepository.findByVersionId("ver_1")).thenReturn(Optional.of(version));
        when(versionRepository.listArtifactsByVersion("ver_1")).thenReturn(List.of(artifact));
        when(idGenerator.generate(IdPrefix.VALIDATION_REPORT)).thenReturn("vrp_1");

        JobContext context = new JobContext("job_1", "VERSION_VALIDATE",
                "{\"versionId\":\"ver_1\"}", 0, "principal_1", "trace_1", "ast_1");

        handler.handle(context);

        assertThat(version.status()).isEqualTo(VersionStatus.PENDING_REVIEW);
        verify(versionRepository).update(version);
        // 先删除旧报告再插入新报告
        verify(jdbcTemplate).update(eq("DELETE FROM validation_report WHERE version_id = ?"), eq("ver_1"));
        verify(jdbcTemplate).update(anyString(), eq("vrp_1"), eq("ver_1"), eq("v1"), eq("PASSED"), anyString(), any(Instant.class));
    }

    @Test
    @DisplayName("manifest digest 缺失 → DRAFT + 写入失败报告")
    void shouldTransitionToDraftWhenManifestMissing() throws Exception {
        Version version = Version.createDraft("ver_1", "ast_1", "v1.0.0", "user_1");
        version.transitionTo(VersionStatus.VALIDATING);
        // 不绑定 manifestDigest

        when(versionRepository.findByVersionId("ver_1")).thenReturn(Optional.of(version));
        when(versionRepository.listArtifactsByVersion("ver_1")).thenReturn(List.of());
        when(idGenerator.generate(IdPrefix.VALIDATION_REPORT)).thenReturn("vrp_1");

        JobContext context = new JobContext("job_1", "VERSION_VALIDATE",
                "{\"versionId\":\"ver_1\"}", 0, "principal_1", "trace_1", "ast_1");

        handler.handle(context);

        assertThat(version.status()).isEqualTo(VersionStatus.DRAFT);
        verify(versionRepository).update(version);
        verify(jdbcTemplate).update(anyString(), eq("vrp_1"), eq("ver_1"), eq("v1"), eq("FAILED"), anyString(), any(Instant.class));
    }

    @Test
    @DisplayName("无工件时校验失败 → DRAFT")
    void shouldFailWhenNoArtifacts() throws Exception {
        Version version = Version.createDraft("ver_1", "ast_1", "v1.0.0", "user_1");
        version.transitionTo(VersionStatus.VALIDATING);
        version.bindManifestDigest("sha256:abc123");

        when(versionRepository.findByVersionId("ver_1")).thenReturn(Optional.of(version));
        when(versionRepository.listArtifactsByVersion("ver_1")).thenReturn(List.of());
        when(idGenerator.generate(IdPrefix.VALIDATION_REPORT)).thenReturn("vrp_1");

        JobContext context = new JobContext("job_1", "VERSION_VALIDATE",
                "{\"versionId\":\"ver_1\"}", 0, "principal_1", "trace_1", "ast_1");

        handler.handle(context);

        assertThat(version.status()).isEqualTo(VersionStatus.DRAFT);
        verify(versionRepository).update(version);
    }
}
