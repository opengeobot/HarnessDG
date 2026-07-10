package com.aihub.version.infrastructure;

import com.aihub.integration.gitea.domain.GiteaTagPublisher;
import com.aihub.integration.gitea.domain.GiteaTagPublisher.TagPublishMode;
import com.aihub.integration.gitea.domain.GiteaTagPublisher.TagPublishResult;
import com.aihub.job.domain.JobContext;
import com.aihub.version.domain.Version;
import com.aihub.version.domain.VersionRepository;
import com.aihub.version.domain.VersionStatus;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.jdbc.core.JdbcTemplate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("PublishJobHandler")
class PublishJobHandlerTest {

    @Mock
    private VersionRepository versionRepository;
    @Mock
    private JdbcTemplate jdbcTemplate;
    @Mock
    private GiteaTagPublisher giteaTagPublisher;

    private PublishJobHandler handler;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @BeforeEach
    void setUp() {
        handler = new PublishJobHandler(versionRepository, jdbcTemplate, objectMapper, giteaTagPublisher);
    }

    @Test
    @DisplayName("应返回正确的任务类型")
    void shouldReturnCorrectType() {
        assertThat(handler.type()).isEqualTo("VERSION_PUBLISH");
    }

    @Test
    @DisplayName("成功发布: APPROVED → PUBLISHED 并调用 Gitea Tag API")
    void shouldPublishSuccessfully() throws Exception {
        Version version = pendingReviewVersion();

        when(jdbcTemplate.queryForMap(anyString(), eq("req_1")))
                .thenReturn(Map.of(
                        "status", "APPROVED",
                        "frozen_digest", "sha256:abc123",
                        "frozen_source_commit", "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa"));
        when(versionRepository.findByVersionId("ver_1")).thenReturn(Optional.of(version));
        when(jdbcTemplate.queryForObject(anyString(), eq(Long.class), eq("v1.0.0"), eq("ver_1"))).thenReturn(0L);
        when(jdbcTemplate.queryForObject(anyString(), eq(String.class), eq("ast_1"))).thenReturn("org/model-a");
        when(giteaTagPublisher.createProtectedTag(anyString(), anyString(), anyString()))
                .thenReturn(new TagPublishResult(TagPublishMode.GITEA_TAG_CREATED, "v1.0.0",
                        "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa"));

        JobContext context = new JobContext("job_1", "VERSION_PUBLISH",
                "{\"requestId\":\"req_1\",\"versionId\":\"ver_1\"}", 0, "principal_1", "trace_1", "ast_1");

        handler.handle(context);

        assertThat(version.status()).isEqualTo(VersionStatus.PUBLISHED);
        verify(versionRepository).update(version);
        verify(giteaTagPublisher).createProtectedTag(
                "org/model-a", "v1.0.0", "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa");
        verify(jdbcTemplate).update(anyString(), eq("req_1"));
        verify(jdbcTemplate).update(anyString(), any(Instant.class), eq("req_1"));
    }

    @Test
    @DisplayName("Gitea 禁用时 PG-only 模式仍可发布")
    void shouldPublishInPgOnlyModeWhenGiteaDisabled() throws Exception {
        Version version = pendingReviewVersion();

        when(jdbcTemplate.queryForMap(anyString(), eq("req_1")))
                .thenReturn(Map.of(
                        "status", "APPROVED",
                        "frozen_digest", "sha256:abc123",
                        "frozen_source_commit", "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa"));
        when(versionRepository.findByVersionId("ver_1")).thenReturn(Optional.of(version));
        when(jdbcTemplate.queryForObject(anyString(), eq(Long.class), eq("v1.0.0"), eq("ver_1"))).thenReturn(0L);
        when(jdbcTemplate.queryForObject(anyString(), eq(String.class), eq("ast_1"))).thenReturn("org/model-a");
        when(giteaTagPublisher.createProtectedTag(anyString(), anyString(), anyString()))
                .thenReturn(new TagPublishResult(TagPublishMode.PG_ONLY_GITEA_DISABLED, "v1.0.0",
                        "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa"));

        JobContext context = new JobContext("job_1", "VERSION_PUBLISH",
                "{\"requestId\":\"req_1\",\"versionId\":\"ver_1\"}", 0, "principal_1", "trace_1", "ast_1");

        handler.handle(context);

        assertThat(version.status()).isEqualTo(VersionStatus.PUBLISHED);
    }

    @Test
    @DisplayName("非 APPROVED/PUBLISHING 状态应跳过")
    void shouldSkipWhenNotApproved() throws Exception {
        when(jdbcTemplate.queryForMap(anyString(), eq("req_1")))
                .thenReturn(Map.of(
                        "status", "REJECTED",
                        "frozen_digest", "sha256:abc",
                        "frozen_source_commit", "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa"));

        JobContext context = new JobContext("job_1", "VERSION_PUBLISH",
                "{\"requestId\":\"req_1\",\"versionId\":\"ver_1\"}", 0, "principal_1", "trace_1", "ast_1");

        handler.handle(context);

        verify(versionRepository, never()).update(any());
        verify(giteaTagPublisher, never()).createProtectedTag(anyString(), anyString(), anyString());
    }

    @Test
    @DisplayName("版本未找到应标记 FAILED 并抛出异常")
    void shouldMarkFailedWhenVersionNotFound() throws Exception {
        when(jdbcTemplate.queryForMap(anyString(), eq("req_1")))
                .thenReturn(Map.of(
                        "status", "APPROVED",
                        "frozen_digest", "sha256:abc",
                        "frozen_source_commit", "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa"));
        when(versionRepository.findByVersionId("ver_1")).thenReturn(Optional.empty());

        JobContext context = new JobContext("job_1", "VERSION_PUBLISH",
                "{\"requestId\":\"req_1\",\"versionId\":\"ver_1\"}", 0, "principal_1", "trace_1", "ast_1");

        assertThatThrownBy(() -> handler.handle(context))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("version not found");

        verify(jdbcTemplate).update(anyString(), any(Instant.class), eq("req_1"));
    }

    @Test
    @DisplayName("digest 不匹配应标记 FAILED 并抛出异常")
    void shouldMarkFailedOnDigestMismatch() throws Exception {
        Version version = Version.createDraft("ver_1", "ast_1", "v1.0.0", "user_1");
        version.transitionTo(VersionStatus.VALIDATING);
        version.bindManifestDigest("sha256:actual");
        version.bindSourceCommit("aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa");
        version.transitionTo(VersionStatus.PENDING_REVIEW);

        when(jdbcTemplate.queryForMap(anyString(), eq("req_1")))
                .thenReturn(Map.of(
                        "status", "APPROVED",
                        "frozen_digest", "sha256:expected",
                        "frozen_source_commit", "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa"));
        when(versionRepository.findByVersionId("ver_1")).thenReturn(Optional.of(version));

        JobContext context = new JobContext("job_1", "VERSION_PUBLISH",
                "{\"requestId\":\"req_1\",\"versionId\":\"ver_1\"}", 0, "principal_1", "trace_1", "ast_1");

        assertThatThrownBy(() -> handler.handle(context))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("digest mismatch");
    }

    @Test
    @DisplayName("git tag 冲突应标记 FAILED 并抛出异常")
    void shouldMarkFailedOnTagConflict() throws Exception {
        Version version = pendingReviewVersion();

        when(jdbcTemplate.queryForMap(anyString(), eq("req_1")))
                .thenReturn(Map.of(
                        "status", "APPROVED",
                        "frozen_digest", "sha256:abc123",
                        "frozen_source_commit", "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa"));
        when(versionRepository.findByVersionId("ver_1")).thenReturn(Optional.of(version));
        when(jdbcTemplate.queryForObject(anyString(), eq(Long.class), eq("v1.0.0"), eq("ver_1"))).thenReturn(1L);

        JobContext context = new JobContext("job_1", "VERSION_PUBLISH",
                "{\"requestId\":\"req_1\",\"versionId\":\"ver_1\"}", 0, "principal_1", "trace_1", "ast_1");

        assertThatThrownBy(() -> handler.handle(context))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("tag conflict");
    }

    private static Version pendingReviewVersion() {
        Version version = Version.createDraft("ver_1", "ast_1", "v1.0.0", "user_1");
        version.transitionTo(VersionStatus.VALIDATING);
        version.bindManifestDigest("sha256:abc123");
        version.bindSourceCommit("aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa");
        version.transitionTo(VersionStatus.PENDING_REVIEW);
        return version;
    }
}
