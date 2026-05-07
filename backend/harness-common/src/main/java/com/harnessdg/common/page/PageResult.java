/**
 * 功能：分页响应结果
 * 时间：2026-05-07
 * 作者：AxeXie
 */
package com.harnessdg.common.page;

import lombok.Data;

import java.util.List;

@Data
public class PageResult<T> {

    private List<T> items;
    private long total;
    private int page;
    private int pageSize;

    public static <T> PageResult<T> of(List<T> items, long total, int page, int pageSize) {
        PageResult<T> result = new PageResult<>();
        result.items = items;
        result.total = total;
        result.page = page;
        result.pageSize = pageSize;
        return result;
    }

    public int getTotalPages() {
        return (int) Math.ceil((double) total / pageSize);
    }
}
