/*
 * 功能: 资产检索性能基准测试——验证资产搜索 P95 延迟是否满足 REQ-PERF-001（<= 500ms）。
 *       使用 Testcontainers PostgreSQL + 真实 Flyway 迁移 + 批量测试数据。
 * 时间: 2026-07-30
 * 作者: AxeXie
 */
package com.aihub.performance;

import static org.assertj.core.api.Assertions.assertThat;

import com.aihub.asset.domain.AssetType;
import com.aihub.asset.domain.AssetSearchCriteria;
import com.aihub.asset.domain.AssetStatus;
import com.aihub.asset.domain.Visibility;
import com.aihub.asset.infrastructure.AssetSearchDao;
import com.aihub.authorization.domain.AccessScope;
import com.aihub.shared.api.CursorPage;
import com.aihub.shared.identity.PrincipalContext;
import com.aihub.shared.identity.PrincipalContextHolder;
import com.aihub.shared.identity.PrincipalType;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
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
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * 资产检索性能基准测试。
 *
 * <p>目标（REQ-PERF-001）：
 * <ul>
 *   <li>关键词搜索 P95 <= 500ms（含 cursor 分页）</li>
 *   <li>Facet 统计 P95 <= 500ms</li>
 *   <li>Cursor 分页翻页 P95 <= 500ms</li>
 * </ul>
 *
 * <p>测试数据规模：10 000 条 asset（MODEL/DATASET 混合），含扩展表与版本记录。
 * 无 Docker 时自动跳过（{@code disabledWithoutDocker = true}），不阻断 {@code ./mvnw verify}。
 */
@Testcontainers(disabledWithoutDocker = true)
@SpringBootTest
@ActiveProfiles("test")
class AssetSearchPerformanceTest {

    private static final int SEED_ASSET_COUNT = 10_000;
    private static final int SEED_VERSIONS_PER_ASSET = 2;
    private static final int WARMUP_ROUNDS = 5;
    private static final int MEASUREMENT_ROUNDS = 50;
    private static final long P95_THRESHOLD_MS = 500L;

    @Container
    static final PostgreSQLContainer<?> POSTGRES =
            new PostgreSQLContainer<>("postgres:17-alpine")
                    .withDatabaseName("aihub_perf_search")
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
    private AssetSearchDao assetSearchDao;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private static final AtomicBoolean DATA_SEEDED = new AtomicBoolean(false);

    @BeforeEach
    void seedDataOnce() {
        if (DATA_SEEDED.compareAndSet(false, true)) {
            PerformanceFixtureGenerator generator = new PerformanceFixtureGenerator();

            List<String> assetIds = generator.generateAssets(jdbcTemplate, SEED_ASSET_COUNT);
            generator.generateVersions(jdbcTemplate, assetIds, SEED_VERSIONS_PER_ASSET);

            int auditCount = (int) (SEED_ASSET_COUNT * SEED_VERSIONS_PER_ASSET * 1.5 / 5);
            generator.generateAuditLogs(jdbcTemplate, auditCount);

            System.out.println("[" + AssetSearchPerformanceTest.class.getSimpleName() + "] "
                    + "Seeded " + SEED_ASSET_COUNT + " assets, "
                    + SEED_VERSIONS_PER_ASSET + " versions/asset, ~" + auditCount + " audit logs");
        }
    }

    @AfterEach
    void clearPrincipalContext() {
        PrincipalContextHolder.clear();
    }

    /**
     * 关键词搜索性能测试。
     *
     * <p>P95 <= 500ms。搜索包含 ILIKE 匹配 name/display_name/description/tags。
     */
    @Test
    @DisplayName("关键词搜索 P95 <= 500ms")
    void keywordSearchPerformance() {
        setAdminPrincipal();
        String keyword = "test";

        // Warmup: JIT 编译 + 缓存预热
        for (int i = 0; i < WARMUP_ROUNDS; i++) {
            assetSearchDao.search(searchCriteria(keyword));
        }

        // Measure
        List<Duration> durations = new ArrayList<>(MEASUREMENT_ROUNDS);
        for (int i = 0; i < MEASUREMENT_ROUNDS; i++) {
            Instant start = Instant.now();
            CursorPage<?> result = assetSearchDao.search(searchCriteria(keyword));
            durations.add(Duration.between(start, Instant.now()));
            assertThat(result).isNotNull();
        }

        PercentileReport report = reportPercentiles("Keyword Search", durations);
        assertThat(report.p95)
                .as("Asset keyword search P95 should be <= 500ms (REQ-PERF-001)")
                .isLessThanOrEqualTo(P95_THRESHOLD_MS);
    }

    /**
     * 类型过滤搜索性能测试。
     */
    @Test
    @DisplayName("类型过滤搜索 P95 <= 500ms")
    void typeFilteredSearchPerformance() {
        setAdminPrincipal();

        // Warmup
        for (int i = 0; i < WARMUP_ROUNDS; i++) {
            assetSearchDao.search(searchCriteria(null, AssetType.MODEL));
        }

        // Measure
        List<Duration> durations = new ArrayList<>(MEASUREMENT_ROUNDS);
        for (int i = 0; i < MEASUREMENT_ROUNDS; i++) {
            Instant start = Instant.now();
            CursorPage<?> result = assetSearchDao.search(searchCriteria(null, AssetType.MODEL));
            durations.add(Duration.between(start, Instant.now()));
            assertThat(result).isNotNull();
        }

        PercentileReport report = reportPercentiles("Type-Filtered Search", durations);
        assertThat(report.p95)
                .as("Type-filtered asset search P95 should be <= 500ms (REQ-PERF-001)")
                .isLessThanOrEqualTo(P95_THRESHOLD_MS);
    }

    /**
     * Facet 统计性能测试。
     */
    @Test
    @DisplayName("Facet 统计 P95 <= 500ms")
    void facetCountPerformance() {
        setAdminPrincipal();
        AssetSearchCriteria criteria = searchCriteria(null);

        // Warmup
        for (int i = 0; i < WARMUP_ROUNDS; i++) {
            assetSearchDao.facet(criteria);
        }

        // Measure
        List<Duration> durations = new ArrayList<>(MEASUREMENT_ROUNDS);
        for (int i = 0; i < MEASUREMENT_ROUNDS; i++) {
            Instant start = Instant.now();
            var result = assetSearchDao.facet(criteria);
            durations.add(Duration.between(start, Instant.now()));
            assertThat(result).isNotNull();
        }

        PercentileReport report = reportPercentiles("Facet Count", durations);
        assertThat(report.p95)
                .as("Asset facet count P95 should be <= 500ms (REQ-PERF-001)")
                .isLessThanOrEqualTo(P95_THRESHOLD_MS);
    }

    /**
     * Cursor 分页性能测试。
     *
     * <p>验证多页翻页的端到端性能。
     */
    @Test
    @DisplayName("Cursor 分页翻页 P95 <= 500ms")
    void cursorPaginationPerformance() {
        setAdminPrincipal();

        // Measure first 20 pages of cursor pagination
        List<Duration> pageDurations = new ArrayList<>();
        String cursor = null;
        for (int page = 0; page < 20; page++) {
            AssetSearchCriteria criteria = searchCriteriaWithCursor(null, cursor);

            Instant start = Instant.now();
            CursorPage<?> result = assetSearchDao.search(criteria);
            Duration elapsed = Duration.between(start, Instant.now());
            pageDurations.add(elapsed);
            cursor = result.nextCursor();
            if (result.hasMore()) {
                assertThat(cursor).isNotNull();
            }
        }

        PercentileReport report = reportPercentiles("Cursor Pagination (20 pages)", pageDurations);
        assertThat(report.p95)
                .as("Cursor pagination P95 should be <= 500ms (REQ-PERF-001)")
                .isLessThanOrEqualTo(P95_THRESHOLD_MS);
    }

    // -----------------------------------------------------------------------
    // 辅助方法
    // -----------------------------------------------------------------------

    private void setAdminPrincipal() {
        PrincipalContextHolder.set(new PrincipalContext(
                "usr_perf_admin", PrincipalType.USER, null, null,
                List.of(), Set.of("admin"),
                Set.of("asset:read", "asset:manage", "authorization:manage"),
                99, null, null, null));
    }

    private AssetSearchCriteria searchCriteria(String keyword) {
        AccessScope adminScope = new AccessScope("usr_perf_admin", true,
                Set.of(), Set.of(), Set.of(), Set.of());
        return new AssetSearchCriteria(
                keyword, null, null, null, null, null, null, null, null, null,
                Set.of(AssetStatus.ACTIVE, AssetStatus.DEPRECATED),
                Set.of(Visibility.PUBLIC, Visibility.INTERNAL, Visibility.PRIVATE),
                adminScope, null, null, null, null, null, null, null, 20);
    }

    private AssetSearchCriteria searchCriteria(String keyword, AssetType type) {
        AccessScope adminScope = new AccessScope("usr_perf_admin", true,
                Set.of(), Set.of(), Set.of(), Set.of());
        return new AssetSearchCriteria(
                keyword, type, null, null, null, null, null, null, null, null,
                Set.of(AssetStatus.ACTIVE, AssetStatus.DEPRECATED),
                Set.of(Visibility.PUBLIC, Visibility.INTERNAL, Visibility.PRIVATE),
                adminScope, null, null, null, null, null, null, null, 20);
    }

    private AssetSearchCriteria searchCriteriaWithCursor(String keyword, String cursor) {
        AccessScope adminScope = new AccessScope("usr_perf_admin", true,
                Set.of(), Set.of(), Set.of(), Set.of());
        return new AssetSearchCriteria(
                keyword, null, null, null, null, null, null, null, null, null,
                Set.of(AssetStatus.ACTIVE, AssetStatus.DEPRECATED),
                Set.of(Visibility.PUBLIC, Visibility.INTERNAL, Visibility.PRIVATE),
                adminScope, null, null, null, null, null, null, cursor, 20);
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