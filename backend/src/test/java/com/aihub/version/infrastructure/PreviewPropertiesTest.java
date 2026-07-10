/*
 * 功能: PreviewProperties 单元测试——验证默认值回退逻辑。
 * 时间: 2026-07-10
 * 作者: AxeXie
 */
package com.aihub.version.infrastructure;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("PreviewProperties")
class PreviewPropertiesTest {

    @Test
    @DisplayName("合法参数构造不改变值")
    void validParametersRemainUnchanged() {
        var props = new PreviewProperties(200, 30, 2048L);
        assertEquals(200, props.maxRows());
        assertEquals(30, props.maxCols());
        assertEquals(2048L, props.maxBytes());
    }

    @Test
    @DisplayName("maxRows <= 0 时默认为 100")
    void maxRowsDefaultsTo100WhenNonPositive() {
        var props = new PreviewProperties(0, 10, 512L);
        assertEquals(100, props.maxRows());

        var propsNegative = new PreviewProperties(-5, 10, 512L);
        assertEquals(100, propsNegative.maxRows());
    }

    @Test
    @DisplayName("maxCols <= 0 时默认为 50")
    void maxColsDefaultsTo50WhenNonPositive() {
        var props = new PreviewProperties(10, 0, 512L);
        assertEquals(50, props.maxCols());

        var propsNegative = new PreviewProperties(10, -1, 512L);
        assertEquals(50, propsNegative.maxCols());
    }

    @Test
    @DisplayName("maxBytes <= 0 时默认为 1MB")
    void maxBytesDefaultsTo1MBWhenNonPositive() {
        var props = new PreviewProperties(10, 10, 0L);
        assertEquals(1024L * 1024, props.maxBytes());

        var propsNegative = new PreviewProperties(10, 10, -100L);
        assertEquals(1024L * 1024, propsNegative.maxBytes());
    }

    @Test
    @DisplayName("全负值参数全部走默认值")
    void allNegativeParametersUseDefaults() {
        var props = new PreviewProperties(-1, -1, -1L);
        assertEquals(100, props.maxRows());
        assertEquals(50, props.maxCols());
        assertEquals(1024L * 1024, props.maxBytes());
    }
}
