/*
 * 功能: PublishApplicationService 授权码口径回归（TASK-P0BR-064）——
 *       三处入口必须要求已注册且与 OpenAPI 一致的 asset:manage，审批要求 asset:publish；
 *       未授权主体必须 fail-closed，不得因修复而放宽。
 * 时间: 2026-09-21
 * 作者: AxeXie
 */
package com.aihub.version.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.aihub.audit.application.AuditService;
import com.aihub.authorization.application.AuthorizationService;
import com.aihub.authorization.domain.Permissions;
import com.aihub.job.application.JobApplicationService;
import com.aihub.shared.error.AuthorizationException;
import com.aihub.shared.error.ErrorCode;
import com.aihub.shared.id.IdGenerator;
import com.aihub.shared.identity.PrincipalContext;
import com.aihub.shared.identity.PrincipalContextHolder;
import com.aihub.version.domain.Version;
import com.aihub.version.domain.VersionRepository;
import com.aihub.version.domain.VersionStatus;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * 固化「实现权限码 == 契约 x-required-permissions」的回归断言。
 *
 * <p>背景：三处入口曾要求从未注册进 iam_permission 的 asset:write，
 * 导致任何主体（含平台管理员）恒 403（TASK-P0BR-063 §2 事实 8）。
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("PublishApplicationService 授权码口径")
class PublishApplicationServiceAuthorizationTest {

    @Mock private VersionRepository versionRepository;
    @Mock private JdbcTemplate jdbcTemplate;
    @Mock private IdGenerator idGenerator;
    @Mock private AuthorizationService authorizationService;
    @Mock private AuditService auditService;
    @Mock private JobApplicationService jobApplicationService;
    @Mock private com.aihub.notification.application.NotificationService notificationService;
    @Mock private com.aihub.notification.application.NotificationRecipientResolver recipientResolver;

    private PublishApplicationService service;

    @BeforeEach
    void setUp() {
        service = new PublishApplicationService(versionRepository, jdbcTemplate, idGenerator,
                authorizationService, auditService, jobApplicationService, new ObjectMapper(),
                notificationService, recipientResolver);
        when(idGenerator.generate(any(com.aihub.shared.id.IdPrefix.class))).thenReturn("gen_01");
        PrincipalContextHolder.set(new PrincipalContext("usr_owner", null, null, null,
                List.of(), Set.of(), Set.of(), 0, "zh-CN", "req_01", "trace_064"));
    }

    @AfterEach
    void tearDown() {
        PrincipalContextHolder.clear();
    }

    /** 模拟 AuthorizationService 对未持有权限的主体 fail-closed。 */
    private void denyUnless(String granted) {
        doThrow(new AuthorizationException(ErrorCode.AUTH_PERMISSION_DENIED,
                "missing required permission", Map.of()))
                .when(authorizationService).requirePermission(
                        org.mockito.ArgumentMatchers.argThat(code -> !code.equals(granted)));
    }

    private Version pendingReviewVersion() {
        Version v = Version.createDraft("ver_01", "ast_01", "0.1.0", "usr_owner");
        v.transitionTo(VersionStatus.VALIDATING);
        v.bindManifestDigest("sha256-abc");
        v.bindSourceCommit("aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa");
        v.transitionTo(VersionStatus.PENDING_REVIEW);
        return v;
    }

    private Version publishedVersion() {
        Version v = pendingReviewVersion();
        v.publish("sha256-abc", "0.1.0", "usr_owner");
        return v;
    }

    @Test
    @DisplayName("submitPublishRequest 要求 asset:manage（与 OpenAPI x-required-permissions 一致）")
    void submitPublishRequestRequiresAssetManage() {
        when(versionRepository.findByVersionId("ver_01")).thenReturn(Optional.of(pendingReviewVersion()));
        when(jdbcTemplate.queryForObject(anyString(), eq(Long.class), any())).thenReturn(0L);
        // 远端新增的 PASSED 校验报告门：正例必须提供最新 PASSED 报告
        when(jdbcTemplate.queryForList(anyString(), eq("ver_01")))
                .thenReturn(List.of(Map.of("status", "PASSED", "policy_version", "v1")));

        service.submitPublishRequest("ver_01");

        verify(authorizationService).requirePermission(Permissions.ASSET_MANAGE);
    }

    @Test
    @DisplayName("submitPublishRequest 对缺 asset:manage 的主体 fail-closed 且不落库")
    void submitPublishRequestDeniesWithoutAssetManage() {
        denyUnless("__never_granted__");

        assertThatThrownBy(() -> service.submitPublishRequest("ver_01"))
                .isInstanceOf(AuthorizationException.class);

        verify(jdbcTemplate, org.mockito.Mockito.never()).update(anyString(), any(Object[].class));
    }

    @Test
    @DisplayName("deprecateVersion 要求 asset:manage 并推进状态")
    void deprecateVersionRequiresAssetManage() {
        Version v = publishedVersion();
        when(versionRepository.findByVersionId("ver_01")).thenReturn(Optional.of(v));

        service.deprecateVersion("ver_01");

        verify(authorizationService).requirePermission(Permissions.ASSET_MANAGE);
        verify(versionRepository).update(v);
        assertThat(v.status()).isEqualTo(VersionStatus.DEPRECATED);
    }

    @Test
    @DisplayName("deprecateVersion 对缺权限主体 fail-closed 且不改状态")
    void deprecateVersionDeniesWithoutAssetManage() {
        denyUnless("__never_granted__");

        assertThatThrownBy(() -> service.deprecateVersion("ver_01"))
                .isInstanceOf(AuthorizationException.class);

        verify(versionRepository, org.mockito.Mockito.never()).update(any(Version.class));
    }

    @Test
    @DisplayName("archiveVersion 要求 asset:manage 并推进状态")
    void archiveVersionRequiresAssetManage() {
        Version v = publishedVersion();
        v.transitionTo(VersionStatus.DEPRECATED);
        when(versionRepository.findByVersionId("ver_01")).thenReturn(Optional.of(v));

        service.archiveVersion("ver_01");

        verify(authorizationService).requirePermission(Permissions.ASSET_MANAGE);
        verify(versionRepository).update(v);
        assertThat(v.status()).isEqualTo(VersionStatus.ARCHIVED);
    }

    @Test
    @DisplayName("archiveVersion 对缺权限主体 fail-closed 且不改状态")
    void archiveVersionDeniesWithoutAssetManage() {
        denyUnless("__never_granted__");

        assertThatThrownBy(() -> service.archiveVersion("ver_01"))
                .isInstanceOf(AuthorizationException.class);

        verify(versionRepository, org.mockito.Mockito.never()).update(any(Version.class));
    }

    @Test
    @DisplayName("submitDecision 要求已注册的 asset:publish（契约 publish:approve 口径差异登记卡 §2 事实 5）")
    void submitDecisionRequiresRegisteredAssetPublish() {
        when(jdbcTemplate.queryForMap(anyString(), any())).thenReturn(Map.of(
                "request_id", "pub_01", "version_id", "ver_01",
                "submitted_by", "usr_submitter", "status", "SUBMITTED",
                "frozen_digest", "sha256-abc",
                "frozen_source_commit", "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa"));
        when(versionRepository.findByVersionId("ver_01")).thenReturn(Optional.of(pendingReviewVersion()));

        service.submitDecision("pub_01", "APPROVE", "ok");

        verify(authorizationService).requirePermission(Permissions.ASSET_PUBLISH);
    }
}
