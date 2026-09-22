/*
 * 功能: 版本发布集成测试——以 Testcontainers PostgreSQL 验证四眼审批全流程：
 *       创建草稿 → 校验 → 提交发布 → 审批决策 → 发布 Job 入队。
 *       覆盖 AC-P3-PUB-001（四眼审批）、AC-P3-PUB-002（Commit 漂移拒绝）、
 *       NFR-REL-001（三元组一致性）。
 * 时间: 2026-07-11
 * 作者: AxeXie
 */
package com.aihub.version;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.aihub.asset.application.AssetApplicationService;
import com.aihub.asset.application.CreateAssetCommand;
import com.aihub.asset.application.AssetView;
import com.aihub.asset.domain.AssetType;
import com.aihub.asset.domain.ModelProfile;
import com.aihub.asset.domain.Visibility;
import com.aihub.shared.identity.PrincipalContext;
import com.aihub.shared.identity.PrincipalContextHolder;
import com.aihub.shared.identity.PrincipalType;
import com.aihub.version.application.PublishApplicationService;
import com.aihub.version.application.VersionApplicationService;
import com.aihub.version.application.VersionView;
import com.aihub.version.domain.VersionStatus;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * 版本发布集成测试。
 */
@Testcontainers(disabledWithoutDocker = true)
@SpringBootTest
@ActiveProfiles("test")
class VersionPublishIT {

    private static final String TEST_TEAM_ID = "team_publish_it";
    private static final String SUBMITTER = "usr_submitter_it";
    private static final String REVIEWER = "usr_reviewer_it";

    @Container
    static final PostgreSQLContainer<?> POSTGRES =
            new PostgreSQLContainer<>("postgres:17-alpine")
                    .withDatabaseName("aihub")
                    .withUsername("aihub")
                    .withPassword("aihub");

    @DynamicPropertySource
    static void registerDataSource(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
    }

    @Autowired
    private AssetApplicationService assetService;
    @Autowired
    private VersionApplicationService versionService;
    @Autowired
    private PublishApplicationService publishService;
    @Autowired
    private NamedParameterJdbcTemplate jdbcTemplate;

    private String assetId;

    @BeforeEach
    void seedData() {
        // Seed organization + team
        jdbcTemplate.update(
                "INSERT INTO organization (organization_id, name, status, created_by, created_at, updated_at, row_version) "
                        + "VALUES (:orgId, :name, 'ACTIVE', 'usr_01', NOW(), NOW(), 1) "
                        + "ON CONFLICT (organization_id) DO NOTHING",
                new MapSqlParameterSource()
                        .addValue("orgId", "org_publish_it")
                        .addValue("name", "Publish IT Org"));
        jdbcTemplate.update(
                "INSERT INTO team (team_id, organization_id, name, description, status, created_by, "
                        + "created_at, updated_at, row_version) "
                        + "VALUES (:teamId, :orgId, :name, NULL, 'ACTIVE', 'usr_01', NOW(), NOW(), 1) "
                        + "ON CONFLICT (team_id) DO NOTHING",
                new MapSqlParameterSource()
                        .addValue("teamId", TEST_TEAM_ID)
                        .addValue("orgId", "org_publish_it")
                        .addValue("name", "Publish IT Team"));

        // Create asset as submitter
        setPrincipal(SUBMITTER, Set.of("asset:read", "asset:write", "asset:create"));
        AssetView asset = assetService.createAsset(new CreateAssetCommand(
                AssetType.MODEL, null, null, "pub-it", "pub-model-" + System.nanoTime(),
                "Publish IT Model", "测试发布流程", Visibility.INTERNAL, null,
                List.of("nlp"), null, "Apache-2.0", TEST_TEAM_ID,
                new ModelProfile("pytorch", "text-generation", "decoder-only"),
                null, SUBMITTER));
        assetId = asset.assetId();
    }

    @AfterEach
    void clearPrincipal() {
        PrincipalContextHolder.clear();
    }

    private void setPrincipal(String principalId, Set<String> scopes) {
        PrincipalContextHolder.set(new PrincipalContext(
                principalId, PrincipalType.USER, null, null,
                List.of(), Set.of(), scopes,
                0, "en", "req_it", "trace_it"));
    }

    private void prepareVersionForPublish(String versionId) {
        // Set manifest digest and source commit via direct SQL
        jdbcTemplate.update(
                "UPDATE asset_version SET manifest_digest = :digest, source_commit = :commit "
                        + "WHERE version_id = :vid",
                new MapSqlParameterSource()
                        .addValue("digest", "sha256:abc123def456")
                        .addValue("commit", "abc123def456789")
                        .addValue("vid", versionId));

        // Insert a PASSED validation report
        jdbcTemplate.update(
                "INSERT INTO validation_report (report_id, version_id, status, policy_version, "
                        + "findings, created_at) "
                        + "VALUES (:rid, :vid, 'PASSED', 'v1', '[]'::jsonb, NOW())",
                new MapSqlParameterSource()
                        .addValue("rid", "vrp_" + versionId)
                        .addValue("vid", versionId));
    }

    @Test
    void fullPublishCycleSubmitApproveAndEnqueue() {
        // 1. Create draft version
        setPrincipal(SUBMITTER, Set.of("asset:read", "asset:write", "asset:create"));
        VersionView draft = versionService.createDraftVersion(assetId, "v1.0.0", SUBMITTER);
        String versionId = draft.versionId();
        assertThat(draft.status()).isEqualTo(VersionStatus.DRAFT);

        // 2. Set digest/commit and validation report
        prepareVersionForPublish(versionId);

        // 3. Transition DRAFT → VALIDATING → PENDING_REVIEW
        setPrincipal(SUBMITTER, Set.of("asset:read", "asset:write"));
        VersionView validating = versionService.transitionVersion(versionId, VersionStatus.VALIDATING, SUBMITTER);
        assertThat(validating.status()).isEqualTo(VersionStatus.VALIDATING);

        VersionView pendingReview = versionService.transitionVersion(versionId, VersionStatus.PENDING_REVIEW, SUBMITTER);
        assertThat(pendingReview.status()).isEqualTo(VersionStatus.PENDING_REVIEW);

        // 4. Submit publish request
        String requestId = publishService.submitPublishRequest(versionId);
        assertThat(requestId).startsWith("pub_");

        // 5. Verify idempotent replay returns same requestId
        String replayId = publishService.submitPublishRequest(versionId);
        assertThat(replayId).isEqualTo(requestId);

        // 6. Approve as different reviewer
        setPrincipal(REVIEWER, Set.of("asset:read", "asset:review"));
        publishService.submitDecision(requestId, "APPROVE", "LGTM");

        // 7. Verify publish request status is APPROVED
        String status = jdbcTemplate.getJdbcTemplate().queryForObject(
                "SELECT status FROM publish_request WHERE request_id = ?",
                String.class, requestId);
        assertThat(status).isEqualTo("APPROVED");

        // 8. Verify publish job was enqueued
        Long jobCount = jdbcTemplate.getJdbcTemplate().queryForObject(
                "SELECT COUNT(*) FROM job_task WHERE handler_type = 'VERSION_PUBLISH' AND payload LIKE ?",
                Long.class, "%" + versionId + "%");
        assertThat(jobCount).isGreaterThanOrEqualTo(1);
    }

    @Test
    void rejectDecisionReturnsVersionToDraft() {
        setPrincipal(SUBMITTER, Set.of("asset:read", "asset:write"));
        VersionView draft = versionService.createDraftVersion(assetId, "v2.0.0", SUBMITTER);
        String versionId = draft.versionId();
        prepareVersionForPublish(versionId);

        versionService.transitionVersion(versionId, VersionStatus.VALIDATING, SUBMITTER);
        versionService.transitionVersion(versionId, VersionStatus.PENDING_REVIEW, SUBMITTER);

        String requestId = publishService.submitPublishRequest(versionId);

        // Reject as reviewer
        setPrincipal(REVIEWER, Set.of("asset:read", "asset:review"));
        publishService.submitDecision(requestId, "REJECT", "Not ready");

        // Verify version returned to DRAFT
        VersionView afterReject = versionService.getVersion(versionId);
        assertThat(afterReject.status()).isEqualTo(VersionStatus.DRAFT);

        // Verify request status is REJECTED
        String status = jdbcTemplate.getJdbcTemplate().queryForObject(
                "SELECT status FROM publish_request WHERE request_id = ?",
                String.class, requestId);
        assertThat(status).isEqualTo("REJECTED");
    }

    @Test
    void submitterCannotApproveOwnRequest() {
        setPrincipal(SUBMITTER, Set.of("asset:read", "asset:write"));
        VersionView draft = versionService.createDraftVersion(assetId, "v3.0.0", SUBMITTER);
        String versionId = draft.versionId();
        prepareVersionForPublish(versionId);

        versionService.transitionVersion(versionId, VersionStatus.VALIDATING, SUBMITTER);
        versionService.transitionVersion(versionId, VersionStatus.PENDING_REVIEW, SUBMITTER);

        String requestId = publishService.submitPublishRequest(versionId);

        // Same submitter tries to approve — should fail
        assertThatThrownBy(() -> publishService.submitDecision(requestId, "APPROVE", "self-approve"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("submitter");
    }

    @Test
    void commitDriftDetectedOnApprove() {
        setPrincipal(SUBMITTER, Set.of("asset:read", "asset:write"));
        VersionView draft = versionService.createDraftVersion(assetId, "v4.0.0", SUBMITTER);
        String versionId = draft.versionId();
        prepareVersionForPublish(versionId);

        versionService.transitionVersion(versionId, VersionStatus.VALIDATING, SUBMITTER);
        versionService.transitionVersion(versionId, VersionStatus.PENDING_REVIEW, SUBMITTER);

        String requestId = publishService.submitPublishRequest(versionId);

        // Simulate commit drift: change source commit after submit
        jdbcTemplate.update(
                "UPDATE asset_version SET source_commit = 'drifted_commit_sha' WHERE version_id = :vid",
                new MapSqlParameterSource("vid", versionId));

        // Approve should detect drift
        setPrincipal(REVIEWER, Set.of("asset:read", "asset:review"));
        assertThatThrownBy(() -> publishService.submitDecision(requestId, "APPROVE", "approve drift"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("commit");
    }
}
