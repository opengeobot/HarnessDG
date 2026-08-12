package com.modelhub.shared.paging;

import com.modelhub.shared.error.ApiException;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.List;
import java.util.Map;

/**
 * Cursor 分页（04 §6.2）：cursor 为不透明签名值（sortVersion:lastKey），limit 默认 50 最大 100。
 * 用于 members、files、commits 等潜在无界集合。
 */
public record CursorQuery(String cursor, long lastKey, int limit) {

    public static final int DEFAULT_LIMIT = 50;
    public static final int MAX_LIMIT = 100;
    private static final int SORT_VERSION = 1;

    public static CursorQuery from(Map<String, String> params) {
        boolean hasPageParams = params.containsKey("page") || params.containsKey("pageSize");
        boolean hasCursorParams = params.containsKey("cursor") || params.containsKey("limit");
        if (hasPageParams && hasCursorParams) {
            throw ApiException.badRequest("page/pageSize 与 cursor/limit 互斥，不能混用",
                    List.of(new ApiException.Detail("cursor", "pagination_mode_conflict")));
        }
        int limit = DEFAULT_LIMIT;
        if (params.containsKey("limit")) {
            try {
                limit = Integer.parseInt(params.get("limit").trim());
            } catch (NumberFormatException e) {
                throw ApiException.badRequest("limit 必须是整数",
                        List.of(new ApiException.Detail("limit", "invalid_format")));
            }
            if (limit < 1 || limit > MAX_LIMIT) {
                throw ApiException.badRequest("limit 必须在 1..100",
                        List.of(new ApiException.Detail("limit", "out_of_range")));
            }
        }
        String cursor = params.get("cursor");
        if (cursor == null || cursor.isBlank()) {
            return new CursorQuery(null, Long.MIN_VALUE, limit);
        }
        return decode(cursor, limit);
    }

    private static CursorQuery decode(String cursor, int limit) {
        try {
            String raw = new String(Base64.getUrlDecoder().decode(cursor), StandardCharsets.UTF_8);
            int sep = raw.indexOf(':');
            int version = Integer.parseInt(raw.substring(0, sep));
            long lastKey = Long.parseLong(raw.substring(sep + 1));
            if (version != SORT_VERSION) {
                throw ApiException.badRequest("cursor 排序版本已过期，请从头翻页",
                        List.of(new ApiException.Detail("cursor", "stale_sort_version")));
            }
            return new CursorQuery(cursor, lastKey, limit);
        } catch (IllegalArgumentException e) {
            throw ApiException.badRequest("cursor 无效",
                    List.of(new ApiException.Detail("cursor", "invalid_format")));
        }
    }

    public static String encode(long lastKey) {
        String raw = SORT_VERSION + ":" + lastKey;
        return Base64.getUrlEncoder().withoutPadding().encodeToString(raw.getBytes(StandardCharsets.UTF_8));
    }
}
