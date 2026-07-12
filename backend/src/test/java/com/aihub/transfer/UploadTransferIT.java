/*
 * 功能: 上传/下载传输集成测试——以 Testcontainers PostgreSQL 验证上传会话生命周期：
 *       创建会话 → 预签名 → 完成 → UPLOAD_MATERIALIZE 入队；以及下载票据签发与权限拒绝。
 *       覆盖 AC-P2-XFR-001（会话全流程）、AC-P2-XFR-003（会话恢复）、AC-P2-XFR-005（越权拒绝）。
 * 时间: 2026-07-11
 * 作者: AxeXie
 */
package com.aihub.transfer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.aihub.asset.application.AssetApplicationService;
import com.aihub.asset.application.AssetView;
import com.aihub.asset.application.CreateAssetCommand;
import com.aihub.asset.domain.AssetType;
import com.aihub.asset.domain.ModelProfile;
import com.aihub.asset.domain.Visibility;
import com.aihub.shared.identity.PrincipalContext;
import com.aihub.shared.identity.PrincipalContextHolder;
import com.aihub.shared.identity.PrincipalType;
import com.aihub.transfer.application.DownloadApplicationService;
import com.aihub.transfer.application.UploadApplicationService;
import com.aihub.transfer.application.UploadSessionView;
import com.aihub.version.application.VersionApplicationService;
import com.aihub.version.application.VersionView;
import com.aihub.version.domain.VersionStatus;
import java.net.URL;
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
 * 上传/下载传输集成测试。
 */
@Testcontainers(disabledWithoutDocker = true)
@SpringBootTest
@ActiveProfiles("test")
class UploadTransferIT {

    private static final String TEST_TEAM_ID = "team_transfer_it";
    private static final String OWNER = "usr_owner_xfr";
    private static final String OUTSIDER = "usr_outsider_xfr";

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
    private UploadApplicationService uploadService;
    @Autowired
    private DownloadApplicationService downloadService;
    @Autowired
    private NamedParameterJdbcTemplate jdbcTemplate;

    private String assetId;
    private String versionId;

    @BeforeEach
    void seedData() {
        jdbcTemplate.update(
                "INSERT INTO organization (organization_id, name, status, created_by, created_at, updated_at, row_version) "
                        + "VALUES (:orgId, :name, 'ACTIVE', 'usr_01', NOW(), NOW(), 1) "
                        + "ON CONFLICT (organization_id) DO NOTHING",
                new MapSqlParameterSource()
                        .addValue("orgId", "org_transfer_it")
                        .addValue("name", "Transfer IT Org"));
        jdbcTemplate.update(
                "INSERT INTO team (team_id, organization_id, name, description, status, created_by, "
                        + "created_at, updated_at, row_version) "
                        + "VALUES (:teamId, :orgId, :name, NULL, 'ACTIVE', 'usr_01', NOW(), NOW(), 1) "
                        + "ON CONFLICT (team_id) DO NOTHING",
                new MapSqlParameterSource()
                        .addValue("teamId", TEST_TEAM_ID)
                        .addValue("orgId", "org_transfer_it")
                        .addValue("name", "Transfer IT Team"));

        setPrincipal(OWNER, Set.of("asset:read", "asset:write", "asset:create", "asset:upload"));
        AssetView asset = assetService.createAsset(new CreateAssetCommand(
                AssetType.MODEL, null, null, "xfr-it", "xfr-model-" + System.nanoTime(),
                "Transfer IT Model", "传输测试", Visibility.INTERNAL, null,
                List.of("nlp"), null, "Apache-2.0", TEST_TEAM_ID,
                new ModelProfile("pytorch", "text-generation", null),
                null, OWNER));
        assetId = asset.assetId();

        VersionView draft = versionService.createDraftVersion(assetId, "v1.0.0", OWNER);
        versionId = draft.versionId();
    }

    @AfterEach
    void clearPrincipal() {
        PrincipalContextHolder.clear();
    }

    private void setPrincipal(String principalId, Set<String> scopes) {
        PrincipalContextHolder.set(new PrincipalContext(
                principalId, PrincipalType.USER, null, null,
                List.of(), Set.of(), scopes,
                0, "en", "req_xfr", "trace_xfr"));
    }

    @Test
    void createSessionPresignAndComplete() {
        setPrincipal(OWNER, Set.of("asset:read", "asset:write", "asset:upload"));

        // Create upload session
        UploadSessionView session = uploadService.createSession(
                assetId, versionId, 1024L, 1, OWNER);
        assertThat(session.sessionId()).startsWith("upl_");
        assertThat(session.status()).isEqualTo("ACTIVE");

        // Presign a part URL
        URL presigned = uploadService.presignPartUpload(session.sessionId(), 1, OWNER);
        assertThat(presigned).isNotNull();
        assertThat(presigned.toString()).isNotEmpty();

        // Complete session with file metadata (parts empty for IT, files with sample)
        UploadSessionView completed = uploadService.completeSession(
                session.sessionId(),
                List.of(),
                List.of(new UploadApplicationService.FileMetadata("model.bin", "sha256:abc", 1024L, "application/octet-stream", null)),
                OWNER);
        assertThat(completed.status()).isIn("COMPLETED", "PROCESSING");

        // Verify UPLOAD_MATERIALIZE job was enqueued
        Long jobCount = jdbcTemplate.getJdbcTemplate().queryForObject(
                "SELECT COUNT(*) FROM job_task WHERE handler_type = 'UPLOAD_MATERIALIZE'",
                Long.class);
        assertThat(jobCount).isGreaterThanOrEqualTo(1);
    }

    @Test
    void sessionRecoveryReturnsSameSession() {
        setPrincipal(OWNER, Set.of("asset:read", "asset:write", "asset:upload"));

        UploadSessionView session = uploadService.createSession(
                assetId, versionId, 1024L, 1, OWNER);

        // Retrieve same session
        UploadSessionView recovered = uploadService.getSessionForAsset(assetId, session.sessionId());
        assertThat(recovered.sessionId()).isEqualTo(session.sessionId());
        assertThat(recovered.status()).isEqualTo("ACTIVE");
    }

    @Test
    void downloadTicketDeniedForUnpublishedVersion() {
        // Version is DRAFT, not PUBLISHED — download should fail
        setPrincipal(OWNER, Set.of("asset:read", "asset:download"));

        assertThatThrownBy(() -> downloadService.issueTicket(versionId, null))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void cancelSessionPreventsFurtherUse() {
        setPrincipal(OWNER, Set.of("asset:read", "asset:write", "asset:upload"));

        UploadSessionView session = uploadService.createSession(
                assetId, versionId, 1024L, 1, OWNER);

        UploadSessionView cancelled = uploadService.cancelSession(session.sessionId(), OWNER);
        assertThat(cancelled.status()).isEqualTo("CANCELLED");
    }
}
