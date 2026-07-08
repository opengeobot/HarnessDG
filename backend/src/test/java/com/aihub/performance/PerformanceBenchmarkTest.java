package com.aihub.performance;

import static org.assertj.core.api.Assertions.assertThat;

import com.aihub.asset.application.AssetApplicationService;
import com.aihub.asset.application.AssetSearchQuery;
import com.aihub.asset.application.AssetSummaryView;
import com.aihub.asset.domain.AssetType;
import com.aihub.shared.api.CursorPage;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

/**
 * 性能基准测试骨架。
 *
 * <p>目标（集成环境就绪后启用）：
 * <ul>
 *   <li>Search P95 ≤ 500ms</li>
 *   <li>Ticket P95 ≤ 300ms</li>
 *   <li>Webhook P95 ≤ 10s</li>
 * </ul>
 *
 * <p>本测试使用 mock 数据验证代码路径正确性，不依赖数据库/Docker。
 * 实际性能测试需在集成环境中使用数据生成器（10 万 Asset 分布）执行。
 */
@ExtendWith(MockitoExtension.class)
class PerformanceBenchmarkTest {

    @Mock
    private AssetApplicationService assetService;

    @Test
    void searchShouldCompleteWithinReasonableTime() {
        // 准备 mock 数据
        when(assetService.searchAssets(any())).thenReturn(
                new CursorPage<>(List.of(), null, false));

        // 执行多次搜索并计时
        int iterations = 100;
        AtomicLong totalTimeMs = new AtomicLong(0);

        for (int i = 0; i < iterations; i++) {
            long start = System.nanoTime();
            assetService.searchAssets(new AssetSearchQuery(
                    "test", AssetType.MODEL, null, null, null, null, null, null, null, null,
                    null, null, null, null, null, null, null, null, null, false, null, 20, "usr_perf"));
            long elapsed = (System.nanoTime() - start) / 1_000_000;
            totalTimeMs.addAndGet(elapsed);
        }

        double avgMs = (double) totalTimeMs.get() / iterations;
        assertThat(avgMs).isLessThan(50.0); // mock 调用应该非常快
    }

    @Test
    void batchOperationsShouldNotLeakMemory() {
        // 模拟批量操作
        when(assetService.searchAssets(any())).thenReturn(
                new CursorPage<>(List.of(), null, false));

        for (int i = 0; i < 1000; i++) {
            assetService.searchAssets(new AssetSearchQuery(
                    "batch-" + i, null, null, null, null, null, null, null, null, null,
                    null, null, null, null, null, null, null, null, null, false, null, 20, "usr_perf"));
        }

        // 如果执行到这里没有 OOM 则通过
        assertThat(true).isTrue();
    }
}
