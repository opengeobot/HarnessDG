/*
 * 功能: 可靠任务仓储集成测试——以真实 PostgreSQL 验证 insert 绑定与读回。
 * 时间: 2026-09-17
 * 作者: AxeXie
 */
package com.aihub.job.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

import com.aihub.bootstrap.AihubApplication;
import com.aihub.job.domain.Job;
import com.aihub.job.domain.JobAttempt;
import com.aihub.job.domain.JobAttemptRepository;
import com.aihub.job.domain.JobClaim;
import com.aihub.job.domain.JobRepository;
import com.aihub.job.domain.JobStatus;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Arrays;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * 可靠任务仓储集成测试。
 *
 * <p>历史缺陷：{@code JdbcJobRepository.insert} 声明 16 列但只绑定 15 个占位符，
 * 真实 pgjdbc 报 {@code The column index is out of range: 16, number of columns: 15}，
 * 导致所有入队路径（首当其冲是资产创建）返回 500。本测试在真实 PostgreSQL 上执行 insert 并读回，
 * 不依赖 mock 驱动，是该缺陷的真实回归门禁。
 *
 * <p>无 Docker 时整体跳过，不阻断 {@code ./mvnw verify}。
 *
 * <p>显式声明 {@link AihubApplication} 作为配置类：本测试位于 {@code com.aihub.job.infrastructure}
 * 包，Spring Boot 的 {@code @SpringBootConfiguration} 向上搜索无法到达 {@code com.aihub.bootstrap}，
 * 不显式声明会导致上下文启动失败而非跳过（既有同类 IT 的共性问题，由独立任务治理）。
 */
@Testcontainers(disabledWithoutDocker = true)
@SpringBootTest(classes = AihubApplication.class)
@ActiveProfiles("test")
class JdbcJobRepositoryIT {

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
    private JobRepository jobRepository;

    @Autowired
    private JobAttemptRepository jobAttemptRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @BeforeEach
    void resetJobTables() {
        // claimNext 是全表队列并按 next_run_at 排序，上一个用例遗留的 PENDING 行会被优先领走，
        // 使当前用例刚插入的行不再是候选。每个用例开始前清空，保证领取目标唯一。
        jdbcTemplate.update("DELETE FROM job_attempt");
        jdbcTemplate.update("DELETE FROM job_task");
    }

    @Test
    void insertsJobAndReadsBackEveryBoundColumn() {
        Instant now = Instant.now().truncatedTo(ChronoUnit.MILLIS);
        String jobId = "job_it_insert_0001";
        Job job = new Job(null, jobId, "REPOSITORY_PROVISION",
                "{\"assetId\":\"ast_it_0001\"}", JobStatus.PENDING,
                5, 0, now, null, null, "trace-it-0001", "usr_it",
                "ast_it_0001", null, now, now, 0);

        assertThatCode(() -> jobRepository.insert(job))
                .as("insert 不得再抛 PSQLException(column index out of range)")
                .doesNotThrowAnyException();

        Job stored = jobRepository.findByJobId(jobId).orElseThrow();
        assertThat(stored.id()).isNotNull();
        assertThat(stored.jobId()).isEqualTo(jobId);
        assertThat(stored.type()).isEqualTo("REPOSITORY_PROVISION");
        assertThat(stored.payload()).contains("ast_it_0001");
        assertThat(stored.status()).isEqualTo(JobStatus.PENDING);
        assertThat(stored.maxAttempts()).isEqualTo(5);
        assertThat(stored.attempts()).isZero();
        assertThat(stored.traceId()).isEqualTo("trace-it-0001");
        assertThat(stored.principalId()).isEqualTo("usr_it");
        assertThat(stored.assetId()).isEqualTo("ast_it_0001");
        assertThat(stored.leasedUntil()).isNull();
        assertThat(stored.leasedBy()).isNull();
        assertThat(stored.errorCode()).isNull();
        assertThat(stored.rowVersion()).isZero();
    }

    @Test
    void insertedJobIsClaimableByWorker() {
        Instant now = Instant.now().truncatedTo(ChronoUnit.MILLIS);
        String jobId = "job_it_claim_0001";
        jobRepository.insert(new Job(null, jobId, "REPOSITORY_PROVISION",
                "{\"assetId\":\"ast_it_0002\"}", JobStatus.PENDING,
                5, 0, now, null, null, "trace-it-0002", "usr_it",
                "ast_it_0002", null, now, now, 0));

        JobClaim claim = jobRepository.claimNext("worker-it", now, 60).orElseThrow();
        Job claimed = claim.job();
        assertThat(claimed.jobId()).isEqualTo(jobId);
        assertThat(claimed.status()).isEqualTo(JobStatus.RUNNING);
        assertThat(claimed.leasedBy()).isEqualTo("worker-it");
        assertThat(claimed.leasedUntil()).isNotNull();

        // 领取前为 PENDING，属首次执行；attempts 递增到本次尝试序号。
        assertThat(claim.previousStatus()).isEqualTo(JobStatus.PENDING);
        assertThat(claim.recoveredFromAbandonedLease()).isFalse();
        assertThat(claimed.attempts()).isEqualTo(1);

        // principal/asset/trace 在领取后仍完整，证明 insert 未错位写入相邻列。
        assertThat(claimed.principalId()).isEqualTo("usr_it");
        assertThat(claimed.assetId()).isEqualTo("ast_it_0002");
        assertThat(claimed.traceId()).isEqualTo("trace-it-0002");
        assertThat(claimed.payload()).contains("ast_it_0002");
    }

    @Test
    void expiredLeaseIsReclaimedWithMonotonicAttemptNumber() {
        Instant now = Instant.now().truncatedTo(ChronoUnit.MILLIS);
        String jobId = "job_it_lease_0001";
        jobRepository.insert(new Job(null, jobId, "REPOSITORY_PROVISION",
                "{\"assetId\":\"ast_it_lease\"}", JobStatus.PENDING,
                5, 0, now, null, null, "trace-it-lease", "usr_it",
                "ast_it_lease", null, now, now, 0));

        // 第一次领取：1 秒租约，模拟该 Worker 随后被强制终止。
        JobClaim first = jobRepository.claimNext("worker-killed", now, 1).orElseThrow();
        assertThat(first.job().attempts()).isEqualTo(1);

        // 租约未到期时，另一个 Worker 不得抢走这条仍在运行的任务。
        Instant withinLease = now.plusMillis(500);
        assertThat(jobRepository.claimNext("worker-other", withinLease, 60)).isEmpty();

        // 租约到期后可被重新领取，且必须能识别出「上一个持有者未正常结束」。
        Instant afterLease = now.plusSeconds(2);
        JobClaim recovered = jobRepository.claimNext("worker-recovery", afterLease, 60).orElseThrow();
        assertThat(recovered.recoveredFromAbandonedLease())
                .as("领取前状态应为 RUNNING，以标识本次是租约恢复")
                .isTrue();
        assertThat(recovered.previousStatus()).isEqualTo(JobStatus.RUNNING);
        assertThat(recovered.previousAttempts()).isEqualTo(1);
        assertThat(recovered.job().attempts())
                .as("attempt 序号必须单调递增，不得复用上一次的序号（INV-JOB-005）")
                .isEqualTo(2);
        assertThat(recovered.job().leasedBy()).isEqualTo("worker-recovery");
    }

    @Test
    void attemptsDoNotExceedMaxAttemptsOnRepeatedLeaseRecovery() {
        Instant now = Instant.now().truncatedTo(ChronoUnit.MILLIS);
        String jobId = "job_it_lease_0002";
        jobRepository.insert(new Job(null, jobId, "REPOSITORY_PROVISION",
                "{\"assetId\":\"ast_it_lease2\"}", JobStatus.PENDING,
                2, 0, now, null, null, "trace-it-lease2", "usr_it",
                "ast_it_lease2", null, now, now, 0));

        Instant cursor = now;
        jobRepository.claimNext("worker-a", cursor, 1).orElseThrow();
        cursor = cursor.plusSeconds(2);
        jobRepository.claimNext("worker-b", cursor, 1).orElseThrow();
        cursor = cursor.plusSeconds(2);
        JobClaim third = jobRepository.claimNext("worker-c", cursor, 1).orElseThrow();

        // 预算耗尽后 attempts 不再增长，由 Worker 判定进入 DEAD；否则会无界重试。
        assertThat(third.job().attempts())
                .as("attempts 不得超过 max_attempts")
                .isEqualTo(2);
    }

    @Test
    void recoveredClaimCarriesPreviousUpdatedAtSoAbandonedAttemptCanBePersisted() {
        Instant start = Instant.now().truncatedTo(ChronoUnit.MILLIS);
        String jobId = "job_it_lease_0003";
        jobRepository.insert(new Job(null, jobId, "REPOSITORY_PROVISION",
                "{\"assetId\":\"ast_it_lease3\"}", JobStatus.PENDING,
                5, 0, start, null, null, "trace-it-lease3", "usr_it",
                "ast_it_lease3", null, start, start, 0));

        Instant firstClaimAt = start.plusSeconds(1);
        jobRepository.claimNext("worker-killed", firstClaimAt, 1).orElseThrow();

        Instant afterLease = firstClaimAt.plusSeconds(5);
        JobClaim recovered = jobRepository.claimNext("worker-recovery", afterLease, 60).orElseThrow();

        // job_attempt.started_at 是 NOT NULL：租约恢复时必须能取到「上一次领取时刻」，
        // 否则 Worker 补写被终止尝试时会 Timestamp.from(null) 抛 NPE，
        // 整个租约恢复路径静默失效（该缺陷只能在真实 PostgreSQL 上暴露）。
        assertThat(recovered.previousUpdatedAt())
                .as("租约恢复必须携带上一次领取时刻作为被终止尝试的开始时间")
                .isNotNull()
                .isEqualTo(firstClaimAt);
        assertThatCode(() -> jobAttemptRepository.insert(new JobAttempt(
                null, jobId, recovered.previousAttempts(), recovered.previousUpdatedAt(),
                afterLease, "FAILED", "previous holder was terminated", "JOB_LEASE_EXPIRED",
                null, recovered.job().traceId())))
                .as("以该时间写入被终止尝试必须成功，不得因 null 抛异常")
                .doesNotThrowAnyException();
    }

    @Test
    void attemptTraceIdIsPersistedAndUniqueConstraintEnforcesMonotonicity() {
        Instant now = Instant.now().truncatedTo(ChronoUnit.MILLIS);
        String jobId = "job_it_attempt_0001";

        jobAttemptRepository.insert(new JobAttempt(null, jobId, 1, now, now, "FAILED",
                "boom", "INTERNAL_ERROR", 5L, "trace-attempt-1"));

        String storedTrace = jdbcTemplate.queryForObject(
                "SELECT trace_id FROM job_attempt WHERE job_id = ? AND attempt_no = 1",
                String.class, jobId);
        assertThat(storedTrace).isEqualTo("trace-attempt-1");
        assertThat(jobAttemptRepository.exists(jobId, 1)).isTrue();
        assertThat(jobAttemptRepository.exists(jobId, 2)).isFalse();

        // (job_id, attempt_no) 唯一约束必须真实存在，作为 attempt 单调性的数据库门禁。
        assertThatCode(() -> jobAttemptRepository.insert(new JobAttempt(null, jobId, 1, now, now,
                "FAILED", "duplicate", "INTERNAL_ERROR", 5L, "trace-attempt-1")))
                .as("重复 attempt_no 必须被 uq_job_attempt_job_no 拒绝")
                .isInstanceOf(org.springframework.dao.DataIntegrityViolationException.class);
    }

    @Test
    void insertStatementCoversExactlyTheTablesNonIdentityColumns() throws Exception {
        // 断言「insert 的列清单」与「真实库中除自增主键外的列」完全一致。
        // 该断言能捕获迁移漂移：若新增列（如 V27 的 trace_id）而 insert 未同步，此处即失败。
        // 注意不能按 column_default IS NULL 过滤——status/max_attempts/attempts/next_run_at/
        // created_at/updated_at/row_version 都有 DDL 默认值，那样过滤只会剩 job_id/type，断言恒为假。
        List<String> allColumns = jdbcTemplate.queryForList(
                "SELECT column_name FROM information_schema.columns WHERE table_name = 'job_task'",
                String.class);
        List<String> insertColumns = insertStatementColumns();

        assertThat(insertColumns)
                .as("insert 必须覆盖除自增主键 id 外的每一列，避免列错位或遗漏")
                .containsExactlyInAnyOrderElementsOf(
                        allColumns.stream().filter(column -> !"id".equals(column)).toList());
    }

    /** 从仓储源码中解析 {@code INSERT INTO job_task (...)} 的列清单。 */
    private List<String> insertStatementColumns() throws Exception {
        Path source = Paths.get(
                "src/main/java/com/aihub/job/infrastructure/JdbcJobRepository.java");
        if (!Files.exists(source)) {
            // 兼容从仓库根目录运行测试的工作目录
            source = Paths.get(
                    "backend/src/main/java/com/aihub/job/infrastructure/JdbcJobRepository.java");
        }
        Matcher matcher = Pattern.compile(
                "INSERT\\s+INTO\\s+job_task\\s*\\((?<columns>[^)]*?)\\)",
                Pattern.DOTALL).matcher(Files.readString(source));
        assertThat(matcher.find())
                .as("JdbcJobRepository 中应存在 INSERT INTO job_task 语句")
                .isTrue();
        return Arrays.stream(matcher.group("columns").split(","))
                .map(String::trim)
                .filter(column -> !column.isEmpty())
                .toList();
    }
}
