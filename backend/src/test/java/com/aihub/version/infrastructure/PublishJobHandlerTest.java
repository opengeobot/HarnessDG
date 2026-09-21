/*
 * 功能: PublishJobHandler 单元测试——正向发布、Saga 补偿、补偿失败、Tag 冲突，
 *       并固化 TASK-P0BR-064 修复：时间列一律由 SQL now() 求值，禁止再绑定 Instant 参数。
 * 时间: 2026-09-21
 * 作者: AxeXie
 */
package com.aihub.version.infrastructure;

import com.aihub.job.domain.JobContext;
import com.aihub.version.domain.Version;
import com.aihub.version.domain.VersionRepository;
import com.aihub.version.domain.VersionStatus;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.jdbc.core.JdbcTemplate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mockingDetails;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.doThrow;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("PublishJobHandler")
class PublishJobHandlerTest {

    @Mock private VersionRepository versionRepository;
    @Mock private JdbcTemplate jdbcTemplate;
    private PublishJobHandler handler;
    private final ObjectMapper objectMapper = new ObjectMapper();

    private static final String REQUEST_ID = "pub_001";
    private static final String VERSION_ID = "ver_001";
    private static final String GIT_TAG = "v1.0.0";
    private static final String DIGEST = "sha256-abc123";

    @BeforeEach
    void setUp() {
        handler = new PublishJobHandler(versionRepository, jdbcTemplate, objectMapper);
        // lenient mocks for jdbcTemplate.update with various arg counts
        lenient().when(jdbcTemplate.update(anyString(), any(Object.class))).thenReturn(1);
        lenient().when(jdbcTemplate.update(anyString(), any(), any())).thenReturn(1);
        lenient().when(jdbcTemplate.update(anyString(), any(), any(), any())).thenReturn(1);
        lenient().when(jdbcTemplate.update(anyString(), any(), any(), any(), any())).thenReturn(1);
        lenient().when(jdbcTemplate.update(anyString(), any(), any(), any(), any(), any())).thenReturn(1);
        lenient().when(jdbcTemplate.update(anyString(), any(), any(), any(), any(), any(), any())).thenReturn(1);
        lenient().when(jdbcTemplate.update(anyString(), any(), any(), any(), any(), any(), any(), any())).thenReturn(1);
        lenient().when(jdbcTemplate.update(anyString(), any(), any(), any(), any(), any(), any(), any(), any())).thenReturn(1);
        lenient().when(jdbcTemplate.update(anyString(), any(), any(), any(), any(), any(), any(), any(), any(), any())).thenReturn(1);
        lenient().when(jdbcTemplate.update(anyString(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any())).thenReturn(1);
    }

    /**
     * 创建处于 PENDING_REVIEW 状态的版本（Draft -> Validating -> bindDigest -> PendingReview）。
     */
    private Version buildPendingReviewVersion(String versionId, String assetId, String versionStr,
                                              String digest, String createdBy) {
        Version v = Version.createDraft(versionId, assetId, versionStr, createdBy);
        v.transitionTo(VersionStatus.VALIDATING);
        v.bindManifestDigest(digest);
        v.transitionTo(VersionStatus.PENDING_REVIEW);
        return v;
    }

    /**
     * 前置：P1 审批通过 + P2 版本 PENDING_REVIEW + P3 digest 匹配 + P4 Tag 无冲突。
     */
    private void setupPublishPrerequisites(Version version) {
        when(jdbcTemplate.queryForMap(
                eq("SELECT status, frozen_digest FROM publish_request WHERE request_id = ?"),
                eq(REQUEST_ID)))
                .thenReturn(Map.of("status", "APPROVED", "frozen_digest", DIGEST));

        when(versionRepository.findByVersionId(VERSION_ID)).thenReturn(Optional.of(version));

        when(jdbcTemplate.queryForObject(
                eq("SELECT COUNT(*) FROM asset_version WHERE git_tag = ? AND status = 'PUBLISHED'"),
                eq(Long.class), eq(GIT_TAG)))
                .thenReturn(0L);
    }

    private JobContext buildContext(String requestId, String versionId) throws Exception {
        Map<String, Object> payload = Map.of(
                "requestId", requestId,
                "versionId", versionId);
        return new JobContext(
                "job_01", "VERSION_PUBLISH",
                objectMapper.writeValueAsString(payload),
                0, "usr_01", "trace_01", "ast_01");
    }

    /**
     * TASK-P0BR-064 防回归守卫：任何 UPDATE/INSERT 都不得把 java.time.Instant 作为
     * 参数绑定（PG JDBC 无法推断其 SQL 类型——真实栈上 UPDATE 必失败、被 catch 的
     * INSERT 会静默丢行）；涉及 decided_at/fired_at/occurred_at 的语句必须由数据库求值。
     */
    private void assertNoInstantBindingAndDbEvaluatedTimestamps() {
        List<String> timestamped = new java.util.ArrayList<>();
        List<org.mockito.invocation.Invocation> updates = mockingDetails(jdbcTemplate)
                .getInvocations().stream()
                .filter(i -> "update".equals(i.getMethod().getName()))
                .toList();
        assertThat(updates).isNotEmpty();
        for (org.mockito.invocation.Invocation inv : updates) {
            Object[] args = inv.getArguments();
            assertThat(args).noneMatch(a -> a instanceof Instant);
            String sql = String.valueOf(args[0]);
            if (sql.contains("decided_at") || sql.contains("fired_at") || sql.contains("occurred_at")) {
                assertThat(sql).contains("now()");
                timestamped.add(sql);
            }
        }
        assertThat(timestamped).isNotEmpty();
    }

    // ---- 正向流程测试 ----

    @Test
    @DisplayName("forwardFlowSuccess: P1-P7 全部成功，时间列由数据库求值")
    void forwardFlowSuccess() throws Exception {
        Version version = buildPendingReviewVersion(VERSION_ID, "ast_01", GIT_TAG, DIGEST, "usr_01");
        setupPublishPrerequisites(version);

        JobContext ctx = buildContext(REQUEST_ID, VERSION_ID);
        handler.handle(ctx);

        // 验证版本状态推进到 PUBLISHED
        verify(versionRepository).update(eq(version));

        // 验证请求状态更新为 PUBLISHED（decided_at 由 SQL now() 求值，无 Instant 参数）
        verify(jdbcTemplate).update(
                eq("UPDATE publish_request SET status = 'PUBLISHED', decided_at = now() WHERE request_id = ?"),
                eq(REQUEST_ID));

        // 验证审计日志记录 PUBLISH_SUCCEEDED
        // recordAuditEvent 传参: auditId, eventType, principalId, action(eventType), requestId,
        //                        result, errorCode, requestSummary, traceId（occurred_at 由 SQL 求值）
        verify(jdbcTemplate).update(
                org.mockito.ArgumentMatchers.argThat(sql -> sql.contains("INSERT INTO audit_log")),
                any(), eq("PUBLISH_SUCCEEDED"), any(), eq("PUBLISH_SUCCEEDED"),
                eq(REQUEST_ID), eq("SUCCEEDED"), isNull(), any(), any());

        assertNoInstantBindingAndDbEvaluatedTimestamps();
    }

    // ---- 补偿重试成功 ----

    @Test
    @DisplayName("p6FailureThenCompensationRetrySucceeds: P7 首次失败，补偿重试成功")
    void p6FailureThenCompensationRetrySucceeds() throws Exception {
        Version forwardVersion = buildPendingReviewVersion(VERSION_ID, "ast_01", GIT_TAG, DIGEST, "usr_01");
        Version retryVersion = buildPendingReviewVersion(VERSION_ID, "ast_01", GIT_TAG, DIGEST, "usr_01");

        // 模拟 DB 状态：正向更新失败，DB 中版本仍为 PENDING_REVIEW，补偿时重新查询返回新版本
        when(versionRepository.findByVersionId(VERSION_ID))
                .thenReturn(Optional.of(forwardVersion))  // P2 check
                .thenReturn(Optional.of(retryVersion));    // compensation retry

        when(jdbcTemplate.queryForMap(
                eq("SELECT status, frozen_digest FROM publish_request WHERE request_id = ?"),
                eq(REQUEST_ID)))
                .thenReturn(Map.of("status", "APPROVED", "frozen_digest", DIGEST));

        when(jdbcTemplate.queryForObject(
                eq("SELECT COUNT(*) FROM asset_version WHERE git_tag = ? AND status = 'PUBLISHED'"),
                eq(Long.class), eq(GIT_TAG)))
                .thenReturn(0L);

        // 模拟 P7 (publish_request PUBLISHED update) 第一次失败、第二次成功
        when(jdbcTemplate.update(
                eq("UPDATE publish_request SET status = 'PUBLISHED', decided_at = now() WHERE request_id = ?"),
                eq(REQUEST_ID)))
                .thenThrow(new RuntimeException("PG connection timeout"))
                .thenReturn(1);

        JobContext ctx = buildContext(REQUEST_ID, VERSION_ID);
        handler.handle(ctx);

        // 验证版本被更新两次（正向 + 补偿）
        verify(versionRepository, times(2)).update(any(Version.class));

        // 验证 PUBLISH 状态最终更新成功（第 2 次调用）
        verify(jdbcTemplate, times(2)).update(
                eq("UPDATE publish_request SET status = 'PUBLISHED', decided_at = now() WHERE request_id = ?"),
                eq(REQUEST_ID));

        // 验证补偿重试成功审计
        verify(jdbcTemplate).update(
                org.mockito.ArgumentMatchers.argThat(sql -> sql.contains("INSERT INTO audit_log")),
                any(), eq("PUBLISH_COMPENSATION_RETRY_SUCCEEDED"), any(),
                eq("PUBLISH_COMPENSATION_RETRY_SUCCEEDED"), eq(REQUEST_ID), eq("SUCCEEDED"),
                isNull(), any(), any());

        assertNoInstantBindingAndDbEvaluatedTimestamps();
    }

    // ---- 补偿失败 ----

    @Test
    @DisplayName("p6FailureAndCompensationRetryFails: P6 和补偿都失败 -> SAGA_COMPENSATION_FAILED")
    void p6FailureAndCompensationRetryFails() throws Exception {
        Version forwardVersion = buildPendingReviewVersion(VERSION_ID, "ast_01", GIT_TAG, DIGEST, "usr_01");
        Version retryVersion = buildPendingReviewVersion(VERSION_ID, "ast_01", GIT_TAG, DIGEST, "usr_01");

        // 模拟 DB 状态：正向更新失败，补偿时重新查询返回新版本（DB 仍为 PENDING_REVIEW）
        when(versionRepository.findByVersionId(VERSION_ID))
                .thenReturn(Optional.of(forwardVersion))  // P2 check
                .thenReturn(Optional.of(retryVersion));    // compensation retry

        when(jdbcTemplate.queryForMap(
                eq("SELECT status, frozen_digest FROM publish_request WHERE request_id = ?"),
                eq(REQUEST_ID)))
                .thenReturn(Map.of("status", "APPROVED", "frozen_digest", DIGEST));

        when(jdbcTemplate.queryForObject(
                eq("SELECT COUNT(*) FROM asset_version WHERE git_tag = ? AND status = 'PUBLISHED'"),
                eq(Long.class), eq(GIT_TAG)))
                .thenReturn(0L);

        // versionRepository.update 始终抛出异常（模拟 PG 持久失败）
        doThrow(new RuntimeException("PG deadlock")).when(versionRepository).update(any(Version.class));

        JobContext ctx = buildContext(REQUEST_ID, VERSION_ID);

        assertThatThrownBy(() -> handler.handle(ctx))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("PG deadlock");

        // 验证 SAGA_COMPENSATION_FAILED 状态更新
        verify(jdbcTemplate).update(
                eq("UPDATE publish_request SET status = 'SAGA_COMPENSATION_FAILED', decided_at = now() WHERE request_id = ?"),
                eq(REQUEST_ID));

        // 验证审计记录 SAGA_COMPENSATION_FAILED
        verify(jdbcTemplate).update(
                org.mockito.ArgumentMatchers.argThat(sql -> sql.contains("INSERT INTO audit_log")),
                any(), eq("SAGA_COMPENSATION_FAILED"), any(),
                eq("SAGA_COMPENSATION_FAILED"), eq(REQUEST_ID), eq("FAILED"),
                org.mockito.ArgumentMatchers.argThat(ec -> ec != null && ((String) ec).contains("RuntimeException")),
                any(), any());

        // 验证告警触发（alertId, title, detail, trace_id = 4 args，fired_at 由 SQL 求值）
        verify(jdbcTemplate).update(
                org.mockito.ArgumentMatchers.argThat(sql -> sql.contains("INSERT INTO system_alert")),
                any(), anyString(), anyString(), any());

        assertNoInstantBindingAndDbEvaluatedTimestamps();
    }

    // ---- Tag 冲突 ----

    @Test
    @DisplayName("p4TagConflictRejected: Tag 已存在 -> FAILED")
    void p4TagConflictRejected() throws Exception {
        Version version = buildPendingReviewVersion(VERSION_ID, "ast_01", GIT_TAG, DIGEST, "usr_01");

        when(jdbcTemplate.queryForMap(
                eq("SELECT status, frozen_digest FROM publish_request WHERE request_id = ?"),
                eq(REQUEST_ID)))
                .thenReturn(Map.of("status", "APPROVED", "frozen_digest", DIGEST));

        when(versionRepository.findByVersionId(VERSION_ID)).thenReturn(Optional.of(version));

        // Tag 已存在（冲突）
        when(jdbcTemplate.queryForObject(
                eq("SELECT COUNT(*) FROM asset_version WHERE git_tag = ? AND status = 'PUBLISHED'"),
                eq(Long.class), eq(GIT_TAG)))
                .thenReturn(1L);

        JobContext ctx = buildContext(REQUEST_ID, VERSION_ID);

        assertThatThrownBy(() -> handler.handle(ctx))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("git tag conflict");

        // 验证请求状态更新为 FAILED（markFailed：decided_at 由 SQL now() 求值）
        verify(jdbcTemplate).update(
                eq("UPDATE publish_request SET status = 'FAILED', decided_at = now() WHERE request_id = ?"),
                eq(REQUEST_ID));

        // 验证 PUBLISH_FAILED 审计真实落写（修复前该 INSERT 因 Instant 绑定被吞 → 静默丢行）
        verify(jdbcTemplate).update(
                org.mockito.ArgumentMatchers.argThat(sql -> sql.contains("INSERT INTO audit_log")),
                any(), eq("PUBLISH_FAILED"), any(), eq("PUBLISH_FAILED"),
                eq(REQUEST_ID), eq("FAILED"), isNull(), any(), any());

        // 验证版本未被发布
        verify(versionRepository, never()).update(any(Version.class));

        // 验证 PUBLISH_SUCCEEDED 审计日志未被记录
        verify(jdbcTemplate, never()).update(
                org.mockito.ArgumentMatchers.argThat(sql -> sql.contains("INSERT INTO audit_log")),
                any(), eq("PUBLISH_SUCCEEDED"), any(), any(), any(), any(), any(), any(), any());

        assertNoInstantBindingAndDbEvaluatedTimestamps();
    }
}
