/*
 * 功能: 游标分页结果，适用于资产检索、审计、任务等高增长列表。
 * 时间: 2026-06-29
 * 作者: AxeXie
 */
package com.aihub.shared.api;

import java.util.List;

/**
 * 游标分页结果。
 *
 * <p>面向高增长列表（资产检索、审计、任务），通过不透明 {@code nextCursor} 翻页，
 * 避免深分页的 offset 性能问题。{@code nextCursor} 为 {@code null} 表示已到末页。
 *
 * @param items      当前页数据
 * @param nextCursor 下一页游标，{@code null} 表示无更多数据
 * @param hasMore    是否还有更多数据
 * @param <T>        元素类型
 */
public record CursorPage<T>(List<T> items, String nextCursor, boolean hasMore) {

    /**
     * 构造末页结果。
     *
     * @param items 当前页数据
     * @param <T>   元素类型
     * @return 无后续游标的分页结果
     */
    public static <T> CursorPage<T> last(List<T> items) {
        return new CursorPage<>(items, null, false);
    }
}
