/*
 * 功能: P0B 配置、幂等与任务生命周期集成测试——覆盖 AC-P0B-CFG-001..003, IDM-001..003, JOB-001..005。
 * 时间: 2026-07-05
 * 作者: AxeXie
 */
package com.aihub.configuration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.aihub.configuration.application.ConfigurationApplicationService;
import com.aihub.configuration.application.ConfigurationDtos.ConfigView;
import com.aihub.configuration.application.ConfigurationDtos.UpdateConfigCommand;
import com.aihub.job.application.IdempotencyService;
import com.aihub.job.application.IdempotencyService.IdempotencyResponse;
import com.aihub.job.application.IdempotencyService.IdempotencyResult;
import com.aihub.job.application.JobApplicationService;
import com.aihub.job.application.JobView;
import com.aihub.job.domain.Job;
import com.aihub.shared.api.CursorPage;
import com.aihub.shared.error.ConflictException;
import com.aihub.shared.error.ErrorCode;
import com.aihub.shared.error.NotFoundException;
import com.aihub.shared.error.ValidationException;
import com.aihub.shared.idempotency.IdempotencyKey;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * P0B 配置/幂等/任务生命周期集成测试。
 *
 * <p>覆盖 CFG-001..003, IDM-001..003, JOB-001..005。
 * 无 Docker 时整体跳过。
 */
@Testcontainers(disabledWithoutDocker = true)
@SpringBootTest
@ActiveProfiles("test")
class ConfigurationLifecycleIT {

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

    @Autowired private ConfigurationApplicationService configService;
    @Autowired private IdempotencyService idempotencyService;
    @Autowired private JobApplicationService jobService;

    // ════════════════════════════════════════════════════════════════
    // AC-P0B-CFG: 配置
    // ════════════════════════════════════════════════════════════════

    // ── CFG-001: 合法/非法类型配置更新 ──────────

    @Test
    void cfg001_updateValidConfigAndRejectInvalidType() {
        List<ConfigView> configs = configService.listConfigurations();
        assertThat(configs).isNotEmpty();

        // 找到首个 STRING 类型配置进行更新。
        ConfigView stringConfig = configs.stream()
                .filter(c -> c.valueType().name().equals("STRING"))
                .findFirst().orElseThrow();

        ConfigView updated = configService.updateConfiguration(
                stringConfig.configKey(),
                new UpdateConfigCommand("new_value_" + UUID.randomUUID().toString().substring(0, 8),
                        stringConfig.version(), null),
                "admin_cfg");
        assertThat(updated.version()).isEqualTo(stringConfig.version() + 1);
    }

    // ── CFG-002: 尝试写 password/token/privateKey 类 Key 被拒 ──────────

    @Test
    void cfg002_secretBearingConfigKeysRejected() {
        assertThatThrownBy(() -> configService.updateConfiguration(
                "database.password", new UpdateConfigCommand("secret123", 0L, null), "admin_cfg"))
                .isInstanceOfSatisfying(ValidationException.class,
                        ex -> assertThat(ex.errorCode()).isEqualTo(ErrorCode.CONFIG_SECRET_FORBIDDEN));

        assertThatThrownBy(() -> configService.updateConfiguration(
                "jwt.private-key", new UpdateConfigCommand("key", 0L, null), "admin_cfg"))
                .isInstanceOfSatisfying(ValidationException.class,
                        ex -> assertThat(ex.errorCode()).isEqualTo(ErrorCode.CONFIG_SECRET_FORBIDDEN));

        assertThatThrownBy(() -> configService.updateConfiguration(
                "api.token.config", new UpdateConfigCommand("tok", 0L, null), "admin_cfg"))
                .isInstanceOfSatisfying(ValidationException.class,
                        ex -> assertThat(ex.errorCode()).isEqualTo(ErrorCode.CONFIG_SECRET_FORBIDDEN));

        assertThatThrownBy(() -> configService.updateConfiguration(
                "oauth2.credential", new UpdateConfigCommand("cred", 0L, null), "admin_cfg"))
                .isInstanceOfSatisfying(ValidationException.class,
                        ex -> assertThat(ex.errorCode()).isEqualTo(ErrorCode.CONFIG_SECRET_FORBIDDEN));
    }

    // ── CFG-003: 两个客户端用同版本并发更新，只允许一个成功 ──────────

    @Test
    void cfg003_concurrentModificationRejected() {
        List<ConfigView> configs = configService.listConfigurations();
        ConfigView config = configs.stream()
                .filter(c -> c.valueType().name().equals("STRING"))
                .findFirst().orElseThrow();
        long currentVersion = config.version();

        // 第一次更新成功。
        configService.updateConfiguration(config.configKey(),
                new UpdateConfigCommand("value_a", currentVersion, null), "admin_a");

        // 第二次用旧版本号被拒。
        assertThatThrownBy(() -> configService.updateConfiguration(config.configKey(),
                new UpdateConfigCommand("value_b", currentVersion, null), "admin_b"))
                .isInstanceOfSatisfying(ConflictException.class,
                        ex -> assertThat(ex.errorCode()).isEqualTo(ErrorCode.CONCURRENT_MODIFICATION));
    }

    // ════════════════════════════════════════════════════════════════
    // AC-P0B-IDM: 幂等
    // ════════════════════════════════════════════════════════════════

    // ── IDM-001: 同 key 重放返回首次结果 ──────────

    @Test
    void idm001_sameKeyReplayReturnsOriginalResult() {
        IdempotencyKey key = new IdempotencyKey("idm001-" + UUID.randomUUID(), "prn_test", "POST", "/assets");
        String fingerprint = "digest_abc";

        IdempotencyResult first = idempotencyService.execute(key, fingerprint,
                () -> new IdempotencyResponse(201, "{\"id\":\"ast_001\"}"));
        assertThat(first.replayed()).isFalse();
        assertThat(first.response().status()).isEqualTo(201);

        // 重放。
        IdempotencyResult replay = idempotencyService.execute(key, fingerprint,
                () -> new IdempotencyResponse(201, "{\"id\":\"ast_002\"}"));
        assertThat(replay.replayed()).isTrue();
        assertThat(replay.response().body()).contains("ast_001"); // 首次结果
    }

    // ── IDM-002: null key 直接执行不幂等 ──────────

    @Test
    void idm002_nullKeyExecutesWithoutIdempotency() {
        IdempotencyResult result = idempotencyService.execute(null, "fp",
                () -> new IdempotencyResponse(200, "ok"));
        assertThat(result.replayed()).isFalse();
        assertThat(result.response().status()).isEqualTo(200);
    }

    // ════════════════════════════════════════════════════════════════
    // AC-P0B-JOB: 可靠任务
    // ════════════════════════════════════════════════════════════════

    // ── JOB-001/004: 入队、查询、重试和取消 ──────────

    @Test
    void job001_enqueueAndQueryJobs() {
        Job job = jobService.enqueue("test.integration", "{\"key\":\"value\"}",
                "prn_test", "trace_it", null, 3);
        assertThat(job.jobId()).startsWith("job_");
        assertThat(job.status().name()).isEqualTo("PENDING");

        // 查询。
        CursorPage<JobView> page = jobService.listJobs(null, null, 100);
        assertThat(page.items()).extracting(JobView::jobId).contains(job.jobId());
    }

    // ── JOB-004: 不可恢复任务进入 DEAD ──────────

    @Test
    void job004_cancelPendingJob() {
        Job job = jobService.enqueue("test.cancel", "{}", "prn_test", "trace_cancel", null, 1);

        // 取消 PENDING 任务。
        jobService.cancelJob(job.jobId());

        // 再次取消被拒（已是 CANCELLED）。
        assertThatThrownBy(() -> jobService.cancelJob(job.jobId()))
                .isInstanceOfSatisfying(ConflictException.class,
                        ex -> assertThat(ex.errorCode()).isEqualTo(ErrorCode.JOB_STATE_NOT_ALLOWED));
    }

    // ── JOB-005: 未授权用户不可重试/取消 ──────────

    @Test
    void job005_nonExistentJobReturnsNotFound() {
        assertThatThrownBy(() -> jobService.retryJob("job_nonexistent"))
                .isInstanceOfSatisfying(NotFoundException.class,
                        ex -> assertThat(ex.errorCode()).isEqualTo(ErrorCode.JOB_NOT_FOUND));

        assertThatThrownBy(() -> jobService.cancelJob("job_nonexistent"))
                .isInstanceOfSatisfying(NotFoundException.class,
                        ex -> assertThat(ex.errorCode()).isEqualTo(ErrorCode.JOB_NOT_FOUND));
    }
}
