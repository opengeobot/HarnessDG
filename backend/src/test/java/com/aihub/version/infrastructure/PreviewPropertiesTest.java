/*
 * 功能: PreviewProperties 单元测试——验证默认值回退逻辑。
 * 时间: 2026-10-10
 * 作者: AxeXie
 */
package com.aihub.version.infrastructure;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("PreviewProperties")
class PreviewPropertiesTest {

    private static PreviewProperties props(int maxRows, int maxCols, long maxBytes,
                                           long maxRenderBytes, long maxTextBytes) {
        return new PreviewProperties(maxRows, maxCols, maxBytes, maxRenderBytes, maxTextBytes,
                null, 0, 0, 0L);
    }

    @Test
    @DisplayName("合法参数构造不改变值")
    void validParametersRemainUnchanged() {
        var props = props(200, 30, 2048L, 4096L, 2048L);
        assertEquals(200, props.maxRows());
        assertEquals(30, props.maxCols());
        assertEquals(2048L, props.maxBytes());
        assertEquals(4096L, props.maxRenderBytes());
        assertEquals(2048L, props.maxTextBytes());
    }

    @Test
    @DisplayName("maxRows <= 0 时默认为 100")
    void maxRowsDefaultsTo100WhenNonPositive() {
        assertEquals(100, props(0, 10, 512L, 0L, 0L).maxRows());
        assertEquals(100, props(-5, 10, 512L, 0L, 0L).maxRows());
    }

    @Test
    @DisplayName("maxCols <= 0 时默认为 50")
    void maxColsDefaultsTo50WhenNonPositive() {
        assertEquals(50, props(10, 0, 512L, 0L, 0L).maxCols());
        assertEquals(50, props(10, -1, 512L, 0L, 0L).maxCols());
    }

    @Test
    @DisplayName("maxBytes <= 0 时默认为 1MB")
    void maxBytesDefaultsTo1MBWhenNonPositive() {
        assertEquals(1024L * 1024, props(10, 10, 0L, 0L, 0L).maxBytes());
        assertEquals(1024L * 1024, props(10, 10, -100L, 0L, 0L).maxBytes());
    }

    @Test
    @DisplayName("maxRenderBytes <= 0 时默认为 5MB（TASK-P0BR-068）")
    void maxRenderBytesDefaultsTo5MBWhenNonPositive() {
        assertEquals(5L * 1024 * 1024, props(10, 10, 512L, 0L, 0L).maxRenderBytes());
        assertEquals(5L * 1024 * 1024, props(10, 10, 512L, -1L, 0L).maxRenderBytes());
    }

    @Test
    @DisplayName("maxTextBytes <= 0 时默认为 1MB（TASK-P0BR-077）")
    void maxTextBytesDefaultsTo1MBWhenNonPositive() {
        assertEquals(1024L * 1024, props(10, 10, 512L, 512L, 0L).maxTextBytes());
        assertEquals(1024L * 1024, props(10, 10, 512L, 512L, -1L).maxTextBytes());
    }

    @Test
    @DisplayName("全负值参数全部走默认值")
    void allNegativeParametersUseDefaults() {
        var props = props(-1, -1, -1L, -1L, -1L);
        assertEquals(100, props.maxRows());
        assertEquals(50, props.maxCols());
        assertEquals(1024L * 1024, props.maxBytes());
        assertEquals(5L * 1024 * 1024, props.maxRenderBytes());
        assertEquals(1024L * 1024, props.maxTextBytes());
    }

    @Test
    @DisplayName("splitDirs 为 null 或全空白时回退缺省白名单（TASK-P0BR-078）")
    void splitDirsFallbackToDefaultsWhenMissingOrBlank() {
        assertEquals(PreviewProperties.DEFAULT_SPLIT_DIRS, props(10, 10, 512L, 512L, 512L).splitDirs());
        var blank = new PreviewProperties(10, 10, 512L, 512L, 512L, List.of(" ", ""), 0, 0, 0L);
        assertEquals(PreviewProperties.DEFAULT_SPLIT_DIRS, blank.splitDirs());
    }

    @Test
    @DisplayName("splitDirs 显式配置时小写化并剔除空白项（TASK-P0BR-078）")
    void splitDirsNormalizedToLowercaseWithoutBlanks() {
        var props = new PreviewProperties(10, 10, 512L, 512L, 512L, Arrays.asList("TRAIN", " ", "custom", null), 0, 0, 0L);
        assertEquals(List.of("train", "custom"), props.splitDirs());
    }

    @Test
    @DisplayName("maxStats* <= 0 时回退 10000 / 200 / 64MB（TASK-P0BR-078）")
    void statsLimitsDefaultWhenNonPositive() {
        var props = props(10, 10, 512L, 512L, 512L);
        assertEquals(10_000, props.maxStatsRows());
        assertEquals(200, props.maxStatsFiles());
        assertEquals(64L * 1024 * 1024, props.maxStatsBytes());

        var explicit = new PreviewProperties(10, 10, 512L, 512L, 512L, List.of("train"), 5, 6, 7L);
        assertEquals(5, explicit.maxStatsRows());
        assertEquals(6, explicit.maxStatsFiles());
        assertEquals(7L, explicit.maxStatsBytes());
    }
}
