/*
 * 功能: 下载票据签发性能基准测试——验证 ticket 生成 P95 延迟是否满足 REQ-PERF-001（<= 300ms）。
 *       使用 Testcontainers PostgreSQL + 真实 Flyway 迁移 + 批量版本数据。
 *       StoragePort 使用 mock（避免 MinIO 依赖），核心测量 DB 查询 + 业务逻辑链路。
 * 时间: 2026-07-30
 * 作者: AxeXie
 */
package com.aihub.performance;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;

import com.aihub.shared.identity.PrincipalContext;
import com.aihub.shared.identity.PrincipalContextHolder;
import com.aihub.shared.identity.PrincipalType;
import com.aihub.transfer.application.DownloadApplicationService;
import com.aihub.transfer.application.DownloadApplicationService.DownloadTicket;
import com.aihub.transfer.domain.StoragePort;
import java.net.URL;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Collectors;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * 下载票据签发性能基准测试。
 *
 * <p>目标（REQ-PERF-001）：
 * <ul>
 *   <li>下载票据签发 P95 <= 300ms（GIT_DVC 和 PRESIGNED_URL 路径）</li>
 * </ul>
 *
 * <p>测试数据规模：1 000 条 PUBLISHED 版本记录（含 artifact），覆盖多资产。
 * StoragePort 使用 mock，核心测量 DB 查询 + 业务逻辑耗时。
 * 无 Docker 时自动跳过（{@code disabledWithoutDocker = true}），不阻断 {@code ./mvnw verify}。
 */
@Testcontainers(disabledWithoutDocker = true)
@SpringBootTest
@ActiveProfiles("test")
class DownloadTicketPerformanceTest {

    private static final int SEED_ASSET_COUNT = 200;
    private static final int VERSIONS_PER_ASSET = 5;
    private static final int WARMUP_ROUNDS = 5;
    private static final int MEASUREMENT_ROUNDS = 50;
    private static final long P95_THRESHOLD_MS = 300L;

    @Container
    static final PostgreSQLContainer<?> POSTGRES =
            new PostgreSQLContainer<>("postgres:17-alpine")
                    .withDatabaseName("aihub_perf_download")
                    .withUsername("aihub")
                    .withPassword("aihub")
                    .withStartupAttempts(3);

    @DynamicPropertySource
    static void registerDataSource(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
    }

    @Autowired
    private DownloadApplicationService downloadService;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @MockitoBean
    private StoragePort storagePort;

    private static final AtomicBoolean DATA_SEEDED = new AtomicBoolean(false);
    private static List<String> publishedVersionIds = new ArrayList<>();
    private static List<String> publishedArtifactIds = new ArrayList<>();

    @BeforeEach
    void seedDataOnce() {
        if (DATA_SEEDED.compareAndSet(false, true)) {
            PerformanceFixtureGenerator generator = new PerformanceFixtureGenerator();

            // Generate assets (all COMPLETED provisioning_status for download eligibility)
            List<String> assetIds = generator.generateAssets(jdbcTemplate, SEED_ASSET_COUNT);

            // Generate versions (mix of PUBLISHED and DRAFT)
            generator.generateVersions(jdbcTemplate, assetIds, VERSIONS_PER_ASSET);

            // Query published version IDs for performance test targets
            publishedVersionIds = jdbcTemplate.queryForList(
                    "SELECT version_id FROM asset_version WHERE status = 'PUBLISHED' ORDER BY RANDOM() LIMIT ?",
                    String.class, MEASUREMENT_ROUNDS);

            // Query artifact IDs for presign path test
            publishedArtifactIds = jdbcTemplate.queryForList(
                    "SELECT va.artifact_id FROM version_artifact va "
                            + "JOIN asset_version av ON av.version_id = va.version_id "
                            + "WHERE av.status = 'PUBLISHED' "
                            + "ORDER BY RANDOM() LIMIT ?",
                    String.class, MEASUREMENT_ROUNDS);

            // Seed audit logs for realistic table size
            generator.generateAuditLogs(jdbcTemplate, SEED_ASSET_COUNT * 2);

            System.out.println("[" + DownloadTicketPerformanceTest.class.getSimpleName() + "] "
                    + "Seeded " + SEED_ASSET_COUNT + " assets with " + VERSIONS_PER_ASSET
                    + " versions/asset. Published versions: " + publishedVersionIds.size()
                    + ", published artifacts: " + publishedArtifactIds.size());

            // Setup mock for StoragePort
            URL mockUrl;
            try {
                mockUrl = new URL("https://mock-minio.test/dvc-cache/test-obj");
            } catch (java.net.MalformedURLException e) {
                throw new RuntimeException(e);
            }
            given(storagePort.presignDownload(any(), any(), any()))
                    .willReturn(mockUrl);
        }
    }

    @AfterEach
    void clearPrincipalContext() {
        PrincipalContextHolder.clear();
    }

    /**
     * GIT_DVC 路径性能测试（无 artifactId，返回 DVC 拉取信息）。
     *
     * <p>P95 <= 300ms。仅涉及 version 和 asset 查询 + 授权检查 + 审计记录。
     */
    @Test
    @DisplayName("GIT_DVC 下载票据签发 P95 <= 300ms")
    void gitDvcTicketPerformance() {
        setPermittedPrincipal();

        // Warmup
        for (int i = 0; i < WARMUP_ROUNDS; i++) {
            int idx = i % publishedVersionIds.size();
            downloadService.issueTicket(publishedVersionIds.get(idx), null);
        }

        // Measure
        List<Duration> durations = new ArrayList<>(MEASUREMENT_ROUNDS);
        for (int i = 0; i < MEASUREMENT_ROUNDS; i++) {
            int idx = i % publishedVersionIds.size();
            String versionId = publishedVersionIds.get(idx);

            Instant start = Instant.now();
            DownloadTicket ticket = downloadService.issueTicket(versionId, null);
            durations.add(Duration.between(start, Instant.now()));

            assertThat(ticket).isNotNull();
            assertThat(ticket.method()).isEqualTo("GIT_DVC");
        }

        PercentileReport report = reportPercentiles("GIT_DVC Ticket", durations);
        assertThat(report.p95)
                .as("GIT_DVC download ticket P95 should be <= 300ms (REQ-PERF-001)")
                .isLessThanOrEqualTo(P95_THRESHOLD_MS);
    }

    /**
     * PRESIGNED_URL 路径性能测试（指定 artifactId，返回预签名 URL）。
     *
     * <p>P95 <= 300ms。包含 version 查询 + asset 查询 + artifact 查询 + 授权检查 + 审计记录。
     * StoragePort.presignDownload 使用 mock（避免 MinIO 网络开销）。
     */
    @Test
    @DisplayName("PRESIGNED_URL 下载票据签发 P95 <= 300ms")
    void presignedUrlTicketPerformance() {
        setPermittedPrincipal();

        // Query published version+artifact pairs
        List<Map<String, Object>> pairs = jdbcTemplate.queryForList(
                "SELECT av.version_id, va.artifact_id FROM version_artifact va "
                        + "JOIN asset_version av ON av.version_id = va.version_id "
                        + "WHERE av.status = 'PUBLISHED' "
                        + "ORDER BY RANDOM() LIMIT ?",
                MEASUREMENT_ROUNDS);

        assertThat(pairs).isNotEmpty();

        // Warmup
        for (int i = 0; i < WARMUP_ROUNDS; i++) {
            Map<String, Object> pair = pairs.get(i);
            downloadService.issueTicket((String) pair.get("version_id"), (String) pair.get("artifact_id"));
        }

        // Measure
        List<Duration> durations = new ArrayList<>(MEASUREMENT_ROUNDS);
        for (int i = 0; i < MEASUREMENT_ROUNDS; i++) {
            Map<String, Object> pair = pairs.get(i % pairs.size());
            String versionId = (String) pair.get("version_id");
            String artifactId = (String) pair.get("artifact_id");

            Instant start = Instant.now();
            DownloadTicket ticket = downloadService.issueTicket(versionId, artifactId);
            durations.add(Duration.between(start, Instant.now()));

            assertThat(ticket).isNotNull();
            assertThat(ticket.method()).isEqualTo("PRESIGNED_URL");
        }

        PercentileReport report = reportPercentiles("PRESIGNED_URL Ticket", durations);
        assertThat(report.p95)
                .as("PRESIGNED_URL download ticket P95 should be <= 300ms (REQ-PERF-001)")
                .isLessThanOrEqualTo(P95_THRESHOLD_MS);
    }

    // -----------------------------------------------------------------------
    // 辅助方法
    // -----------------------------------------------------------------------

    private void setPermittedPrincipal() {
        PrincipalContextHolder.set(new PrincipalContext(
                "usr_perf_download", PrincipalType.USER, null, null,
                List.of(), Set.of("user"),
                Set.of("asset:read", "authorization:manage"),
                99, null, null, null));
    }

    private static PercentileReport reportPercentiles(String label, List<Duration> durations) {
        List<Long> sortedMs = durations.stream()
                .map(d -> d.toMillis())
                .sorted()
                .collect(Collectors.toList());

        long p50 = sortedMs.get((int) Math.ceil(sortedMs.size() * 0.50) - 1);
        long p95 = sortedMs.get((int) Math.ceil(sortedMs.size() * 0.95) - 1);
        long p99 = sortedMs.get((int) Math.ceil(sortedMs.size() * 0.99) - 1);
        long min = sortedMs.get(0);
        long max = sortedMs.get(sortedMs.size() - 1);
        double avg = sortedMs.stream().mapToLong(Long::longValue).average().orElse(0);

        System.out.println("[" + label + "] " + sortedMs.size() + " measurements:");
        System.out.println("  Min:  " + min + "ms");
        System.out.println("  P50:  " + p50 + "ms");
        System.out.println("  P95:  " + p95 + "ms");
        System.out.println("  P99:  " + p99 + "ms");
        System.out.println("  Max:  " + max + "ms");
        System.out.println("  Avg:  " + String.format("%.1f", avg) + "ms");

        return new PercentileReport(min, p50, p95, p99, max, avg, sortedMs.size());
    }

    private record PercentileReport(long min, long p50, long p95, long p99, long max,
                                     double avg, int count) {}
}