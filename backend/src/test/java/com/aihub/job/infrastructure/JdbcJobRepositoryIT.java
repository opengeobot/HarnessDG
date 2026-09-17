/*
 * 功能: 可靠任务仓储集成测试——以真实 PostgreSQL 验证 insert 绑定与读回。
 * 时间: 2026-09-17
 * 作者: AxeXie
 */
package com.aihub.job.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

import com.aihub.job.domain.Job;
import com.aihub.job.domain.JobRepository;
import com.aihub.job.domain.JobStatus;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
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
 */
@Testcontainers(disabledWithoutDocker = true)
@SpringBootTest
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
    private JdbcTemplate jdbcTemplate;

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

        Job claimed = jobRepository.claimNext("worker-it", now, 60).orElseThrow();
        assertThat(claimed.jobId()).isEqualTo(jobId);
        assertThat(claimed.status()).isEqualTo(JobStatus.RUNNING);
        assertThat(claimed.leasedBy()).isEqualTo("worker-it");
        assertThat(claimed.leasedUntil()).isNotNull();

        // principal/asset/trace 在领取后仍完整，证明 insert 未错位写入相邻列。
        assertThat(claimed.principalId()).isEqualTo("usr_it");
        assertThat(claimed.assetId()).isEqualTo("ast_it_0002");
        assertThat(claimed.traceId()).isEqualTo("trace-it-0002");
        assertThat(claimed.payload()).contains("ast_it_0002");
    }

    @Test
    void insertStatementCoversExactlyTheTablesNonIdentityColumns() {
        var insertableColumns = jdbcTemplate.queryForList(
                "SELECT column_name FROM information_schema.columns "
                        + "WHERE table_name = 'job_task' AND is_identity = 'NO' "
                        + "AND column_default IS NULL AND is_nullable = 'NO'",
                String.class);

        assertThat(insertableColumns)
                .as("job_task 的 NOT NULL 且无默认值列必须全部由 insert 显式提供")
                .containsExactlyInAnyOrder("job_id", "type", "status", "max_attempts",
                        "attempts", "next_run_at", "created_at", "updated_at", "row_version");
    }
}
