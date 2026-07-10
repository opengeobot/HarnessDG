package com.aihub.version.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.aihub.audit.application.AuditService;
import com.aihub.authorization.application.AuthorizationService;
import com.aihub.job.application.JobApplicationService;
import com.aihub.shared.error.NotFoundException;
import com.aihub.shared.id.IdGenerator;
import com.aihub.shared.id.IdPrefix;
import com.aihub.shared.identity.PrincipalContext;
import com.aihub.shared.identity.PrincipalContextHolder;
import com.aihub.shared.identity.PrincipalType;
import com.aihub.version.domain.Version;
import com.aihub.version.domain.VersionRepository;
import com.aihub.version.domain.VersionStatus;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * {@link PublishApplicationService} 单元测试。
 */
@ExtendWith(MockitoExtension.class)
class PublishApplicationServiceTest {

    @Mock private VersionRepository versionRepository;
    @Mock private JdbcTemplate jdbcTemplate;
    @Mock private IdGenerator idGenerator;
    @Mock private AuthorizationService authorizationService;
    @Mock private AuditService auditService;
    @Mock private JobApplicationService jobApplicationService;
    @Mock private com.aihub.notification.application.NotificationService notificationService;

    private PublishApplicationService service;

    @BeforeEach
    void setUp() {
        service = new PublishApplicationService(
                versionRepository, jdbcTemplate, idGenerator,
                authorizationService, auditService, jobApplicationService,
                new ObjectMapper(), notificationService);
        PrincipalContextHolder.set(new PrincipalContext(
                "usr_reviewer", PrincipalType.USER, null, null,
                List.of(), Set.of(), Set.of("asset:review", "asset:submit", "asset:deprecate"),
                0, "en", "req_1", "trace_1"));
    }

    @AfterEach
    void tearDown() {
        PrincipalContextHolder.clear();
    }

    @Test
    void submitPublishRequestWhenVersionNotInPendingReviewThrows() {
        Version draft = Version.createDraft("ver_01", "ast_01", "v1.0.0", "usr_01");
        given(versionRepository.findByVersionId("ver_01")).willReturn(Optional.of(draft));

        assertThatThrownBy(() -> service.submitPublishRequest("ver_01"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("PENDING_REVIEW");
    }

    @Test
    void submitPublishRequestWhenManifestDigestMissingThrows() {
        Version v = createPendingReviewVersion("ver_01", null, null);
        given(versionRepository.findByVersionId("ver_01")).willReturn(Optional.of(v));

        assertThatThrownBy(() -> service.submitPublishRequest("ver_01"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("manifest digest");
    }

    @Test
    void submitPublishRequestCreatesIdempotentRequest() {
        Version v = createPendingReviewVersion("ver_01", "sha256abc",
                "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa");
        given(versionRepository.findByVersionId("ver_01")).willReturn(Optional.of(v));
        given(idGenerator.generate(IdPrefix.PUBLISH_REQUEST)).willReturn("pub_001");
        given(jdbcTemplate.queryForObject(
                eq("SELECT COUNT(*) FROM publish_request WHERE version_id = ?"),
                eq(Long.class), eq("ver_01"))).willReturn(0L);
        given(jdbcTemplate.queryForList(anyString(), eq("ver_01")))
                .willReturn(List.of(Map.of("status", "PASSED", "policy_version", "v1")));
        given(jdbcTemplate.update(anyString(), any(), any(), any(), any(), any(), any())).willReturn(1);

        String requestId = service.submitPublishRequest("ver_01");

        assertThat(requestId).isEqualTo("pub_001");
        verify(auditService).record(any());
    }

    @Test
    void submitPublishRequestReturnsExistingWhenIdempotent() {
        Version v = createPendingReviewVersion("ver_01", "sha256abc",
                "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa");
        given(versionRepository.findByVersionId("ver_01")).willReturn(Optional.of(v));
        given(jdbcTemplate.queryForObject(
                eq("SELECT COUNT(*) FROM publish_request WHERE version_id = ?"),
                eq(Long.class), eq("ver_01"))).willReturn(1L);
        given(jdbcTemplate.queryForObject(
                eq("SELECT request_id FROM publish_request WHERE version_id = ?"),
                eq(String.class), eq("ver_01"))).willReturn("pub_existing");

        String requestId = service.submitPublishRequest("ver_01");

        assertThat(requestId).isEqualTo("pub_existing");
    }

    @Test
    void submitPublishRequestWhenVersionNotFoundThrows() {
        given(versionRepository.findByVersionId("ver_missing")).willReturn(Optional.empty());

        assertThatThrownBy(() -> service.submitPublishRequest("ver_missing"))
                .isInstanceOf(NotFoundException.class);
    }

    @Test
    void submitPublishRequestWithoutValidationReportThrows() {
        Version v = createPendingReviewVersion("ver_01", "sha256abc",
                "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa");
        given(versionRepository.findByVersionId("ver_01")).willReturn(Optional.of(v));
        given(jdbcTemplate.queryForObject(
                eq("SELECT COUNT(*) FROM publish_request WHERE version_id = ?"),
                eq(Long.class), eq("ver_01"))).willReturn(0L);
        given(jdbcTemplate.queryForList(anyString(), eq("ver_01"))).willReturn(List.of());

        assertThatThrownBy(() -> service.submitPublishRequest("ver_01"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("validation report");
    }

    @Test
    void submitDecisionApproveRejectsContentDrift() {
        Map<String, Object> request = Map.of(
                "request_id", "pub_01", "version_id", "ver_01",
                "submitted_by", "usr_submitter", "status", "SUBMITTED",
                "frozen_digest", "sha256:frozen",
                "frozen_source_commit", "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa");
        given(jdbcTemplate.queryForMap(anyString(), eq("pub_01"))).willReturn(request);

        Version v = createPendingReviewVersion("ver_01", "sha256:drifted",
                "bbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb");
        given(versionRepository.findByVersionId("ver_01")).willReturn(Optional.of(v));

        assertThatThrownBy(() -> service.submitDecision("pub_01", "APPROVE", "ok"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("drifted");
    }

    @Test
    void submitDecisionRejectReturnsVersionToDraft() {
        Map<String, Object> request = Map.of(
                "request_id", "pub_01", "version_id", "ver_01",
                "submitted_by", "usr_submitter", "status", "SUBMITTED",
                "frozen_digest", "digest",
                "frozen_source_commit", "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa");
        given(jdbcTemplate.queryForMap(anyString(), eq("pub_01"))).willReturn(request);
        given(idGenerator.generate(IdPrefix.REVIEW_DECISION)).willReturn("rvw_001");
        given(jdbcTemplate.update(anyString(), any(), any(), any(), any(), any())).willReturn(1);
        given(jdbcTemplate.update(anyString(), any(), eq("pub_01"))).willReturn(1);

        Version v = createPendingReviewVersion("ver_01", "digest",
                "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa");
        given(versionRepository.findByVersionId("ver_01")).willReturn(Optional.of(v));

        service.submitDecision("pub_01", "REJECT", "needs work");

        assertThat(v.status()).isEqualTo(VersionStatus.DRAFT);
        verify(versionRepository).update(v);
    }

    @Test
    void submitDecisionSelfApprovalThrows() {
        Map<String, Object> request = Map.of(
                "request_id", "pub_01", "version_id", "ver_01",
                "submitted_by", "usr_reviewer", "status", "SUBMITTED",
                "frozen_digest", "digest",
                "frozen_source_commit", "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa");
        given(jdbcTemplate.queryForMap(anyString(), eq("pub_01"))).willReturn(request);

        assertThatThrownBy(() -> service.submitDecision("pub_01", "APPROVE", "ok"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("submitter cannot approve");
    }

    @Test
    void submitDecisionApproveEnqueuesPublishJob() throws Exception {
        Map<String, Object> request = Map.of(
                "request_id", "pub_01", "version_id", "ver_01",
                "submitted_by", "usr_submitter", "status", "SUBMITTED",
                "frozen_digest", "digest",
                "frozen_source_commit", "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa");
        given(jdbcTemplate.queryForMap(anyString(), eq("pub_01"))).willReturn(request);
        given(idGenerator.generate(IdPrefix.REVIEW_DECISION)).willReturn("rvw_002");
        given(jdbcTemplate.update(anyString(), any(), any(), any(), any(), any())).willReturn(1);
        given(jdbcTemplate.update(anyString(), any(), eq("pub_01"))).willReturn(1);
        given(jdbcTemplate.queryForObject(contains("sensitivity_code"), eq(String.class), eq("ast_01")))
                .willReturn("INTERNAL");
        given(jdbcTemplate.queryForObject(contains("COUNT(DISTINCT reviewer_id)"), eq(Long.class), eq("pub_01")))
                .willReturn(1L);

        Version v = createPendingReviewVersion("ver_01", "digest",
                "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa");
        given(versionRepository.findByVersionId("ver_01")).willReturn(Optional.of(v));

        service.submitDecision("pub_01", "APPROVE", "LGTM");

        verify(jobApplicationService).enqueue(eq("VERSION_PUBLISH"), anyString(),
                eq("usr_reviewer"), any(), eq("ver_01"), eq(3));
    }

    @Test
    void submitDecisionApproveHighSensitivityRequiresTwoApprovals() throws Exception {
        Map<String, Object> request = Map.of(
                "request_id", "pub_01", "version_id", "ver_01",
                "submitted_by", "usr_submitter", "status", "SUBMITTED",
                "frozen_digest", "digest",
                "frozen_source_commit", "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa");
        given(jdbcTemplate.queryForMap(anyString(), eq("pub_01"))).willReturn(request);
        given(idGenerator.generate(IdPrefix.REVIEW_DECISION)).willReturn("rvw_004");
        given(jdbcTemplate.update(anyString(), any(), any(), any(), any(), any())).willReturn(1);
        given(jdbcTemplate.queryForObject(contains("sensitivity_code"), eq(String.class), eq("ast_01")))
                .willReturn("SECRET");
        given(jdbcTemplate.queryForObject(contains("COUNT(DISTINCT reviewer_id)"), eq(Long.class), eq("pub_01")))
                .willReturn(1L);

        Version v = createPendingReviewVersion("ver_01", "digest",
                "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa");
        given(versionRepository.findByVersionId("ver_01")).willReturn(Optional.of(v));

        service.submitDecision("pub_01", "APPROVE", "first approval");

        verify(jobApplicationService, never()).enqueue(anyString(), anyString(), anyString(), any(), anyString(), anyInt());
    }

    @Test
    void submitDecisionApproveHighSensitivityEnqueuesAfterSecondApproval() throws Exception {
        Map<String, Object> request = Map.of(
                "request_id", "pub_01", "version_id", "ver_01",
                "submitted_by", "usr_submitter", "status", "SUBMITTED",
                "frozen_digest", "digest",
                "frozen_source_commit", "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa");
        given(jdbcTemplate.queryForMap(anyString(), eq("pub_01"))).willReturn(request);
        given(idGenerator.generate(IdPrefix.REVIEW_DECISION)).willReturn("rvw_005");
        given(jdbcTemplate.update(anyString(), any(), any(), any(), any(), any())).willReturn(1);
        given(jdbcTemplate.update(anyString(), any(), eq("pub_01"))).willReturn(1);
        given(jdbcTemplate.queryForObject(contains("sensitivity_code"), eq(String.class), eq("ast_01")))
                .willReturn("CONFIDENTIAL");
        given(jdbcTemplate.queryForObject(contains("COUNT(DISTINCT reviewer_id)"), eq(Long.class), eq("pub_01")))
                .willReturn(2L);

        Version v = createPendingReviewVersion("ver_01", "digest",
                "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa");
        given(versionRepository.findByVersionId("ver_01")).willReturn(Optional.of(v));

        service.submitDecision("pub_01", "APPROVE", "second approval");

        verify(jobApplicationService).enqueue(eq("VERSION_PUBLISH"), anyString(),
                eq("usr_reviewer"), any(), eq("ver_01"), eq(3));
    }

    @Test
    void submitDecisionRequestChangesReturnsVersionToDraft() {
        Map<String, Object> request = Map.of(
                "request_id", "pub_01", "version_id", "ver_01",
                "submitted_by", "usr_submitter", "status", "SUBMITTED",
                "frozen_digest", "digest",
                "frozen_source_commit", "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa");
        given(jdbcTemplate.queryForMap(anyString(), eq("pub_01"))).willReturn(request);
        given(idGenerator.generate(IdPrefix.REVIEW_DECISION)).willReturn("rvw_003");
        given(jdbcTemplate.update(anyString(), any(), any(), any(), any(), any())).willReturn(1);
        given(jdbcTemplate.update(anyString(), any(), eq("pub_01"))).willReturn(1);

        Version v = createPendingReviewVersion("ver_01", "digest",
                "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa");
        given(versionRepository.findByVersionId("ver_01")).willReturn(Optional.of(v));

        service.submitDecision("pub_01", "REQUEST_CHANGES", "please fix X");

        assertThat(v.status()).isEqualTo(VersionStatus.DRAFT);
        verify(versionRepository).update(v);
    }

    private Version createPendingReviewVersion(String versionId, String digest, String sourceCommit) {
        Version v = Version.createDraft(versionId, "ast_01", "v1.0.0", "usr_01");
        v.transitionTo(VersionStatus.VALIDATING);
        if (digest != null) v.bindManifestDigest(digest);
        if (sourceCommit != null) v.bindSourceCommit(sourceCommit);
        v.transitionTo(VersionStatus.PENDING_REVIEW);
        return v;
    }
}
