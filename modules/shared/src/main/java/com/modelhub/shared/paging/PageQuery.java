package com.modelhub.shared.paging;

import com.modelhub.shared.error.ApiException;

import java.util.List;
import java.util.Map;

/**
 * 页码分页（04 §6.1）：page 从 1 开始，pageSize 默认 12 最大 100。
 * 与 cursor 模式互斥，混用返回 400 VALIDATION_FAILED（ADR-003）。
 */
public record PageQuery(int page, int pageSize) {

    public static final int DEFAULT_PAGE_SIZE = 12;
    public static final int MAX_PAGE_SIZE = 100;
    public static final int MAX_PAGE = 10_000;

    public static PageQuery from(Map<String, String> params) {
        boolean hasPageParams = params.containsKey("page") || params.containsKey("pageSize");
        boolean hasCursorParams = params.containsKey("cursor") || params.containsKey("limit");
        if (hasPageParams && hasCursorParams) {
            throw ApiException.badRequest("page/pageSize 与 cursor/limit 互斥，不能混用",
                    List.of(new ApiException.Detail("page", "pagination_mode_conflict")));
        }
        if (!hasPageParams) {
            return new PageQuery(1, DEFAULT_PAGE_SIZE);
        }
        int page = parseInt(params.get("page"), "page", 1);
        int pageSize = parseInt(params.get("pageSize"), "pageSize", DEFAULT_PAGE_SIZE);
        if (page < 1 || page > MAX_PAGE) {
            throw ApiException.badRequest("page 超出允许范围",
                    List.of(new ApiException.Detail("page", "out_of_range")));
        }
        if (pageSize < 1 || pageSize > MAX_PAGE_SIZE) {
            throw ApiException.badRequest("pageSize 必须在 1..100",
                    List.of(new ApiException.Detail("pageSize", "out_of_range")));
        }
        return new PageQuery(page, pageSize);
    }

    private static int parseInt(String raw, String field, int defaultValue) {
        if (raw == null || raw.isBlank()) {
            return defaultValue;
        }
        try {
            return Integer.parseInt(raw.trim());
        } catch (NumberFormatException e) {
            throw ApiException.badRequest(field + " 必须是整数",
                    List.of(new ApiException.Detail(field, "invalid_format")));
        }
    }

    public int offset() {
        return (page - 1) * pageSize;
    }
}
