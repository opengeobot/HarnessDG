package com.modelhub.artifact.service;

import com.modelhub.shared.error.ApiException;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.List;

/**
 * branches/commits/files 分页的本地不透明游标（ADR-003 / 04 §6.2）：
 * cursor 为 base64url 包装的 sortVersion 载荷，客户端不可解读、不可构造探测。
 * branches/commits 对 Gitea 页号投影（offset 型）：base64url("1:" + pageIndex)；
 * files 为 keyset（排序 path ASC + id ASC 稳定 tie-breaker）：
 * base64url("1:" + base64url(path) + ":" + id)，path 内嵌 base64 保证分隔符 ':' 不会出现在字段内。
 * 解码失败一律 400 VALIDATION_FAILED（与 shared CursorQuery / catalog RepoListCursor 同语义），
 * 排序版本不匹配 → stale_sort_version，要求从头翻页。
 */
final class BrowseCursor {

    static final int SORT_VERSION = 1;

    /** files keyset 续读位置（上一页最后一条的 path + id）。 */
    record FileKey(String path, long id) {}

    private BrowseCursor() {}

    static String encodePage(int pageIndex) {
        return Base64.getUrlEncoder().withoutPadding()
                .encodeToString((SORT_VERSION + ":" + pageIndex).getBytes(StandardCharsets.UTF_8));
    }

    /** 空白 cursor → 0（首页）；坏格式/负页号 → 400。 */
    static int decodePage(String cursor) {
        if (cursor == null || cursor.isBlank()) {
            return 0;
        }
        String[] parts = decodeParts(cursor);
        if (parts.length != 2) {
            throw invalid();
        }
        int pageIndex;
        try {
            pageIndex = Integer.parseInt(parts[1]);
        } catch (NumberFormatException e) {
            throw invalid();
        }
        if (pageIndex < 0) {
            throw invalid();
        }
        return pageIndex;
    }

    static String encodeFileKey(String path, long id) {
        String pathB64 = Base64.getUrlEncoder().withoutPadding()
                .encodeToString(path.getBytes(StandardCharsets.UTF_8));
        String raw = SORT_VERSION + ":" + pathB64 + ":" + id;
        return Base64.getUrlEncoder().withoutPadding()
                .encodeToString(raw.getBytes(StandardCharsets.UTF_8));
    }

    /** 空白 cursor → null（从头）；坏格式 → 400。 */
    static FileKey decodeFileKey(String cursor) {
        if (cursor == null || cursor.isBlank()) {
            return null;
        }
        String[] parts = decodeParts(cursor);
        if (parts.length != 3) {
            throw invalid();
        }
        try {
            String path = new String(Base64.getUrlDecoder().decode(parts[1]), StandardCharsets.UTF_8);
            long id = Long.parseLong(parts[2]);
            return new FileKey(path, id);
        } catch (IllegalArgumentException e) {
            throw invalid();
        }
    }

    /** 解开外层 base64url 并校验排序版本；返回 ':' 分隔的载荷段。 */
    private static String[] decodeParts(String cursor) {
        String raw;
        try {
            raw = new String(Base64.getUrlDecoder().decode(cursor), StandardCharsets.UTF_8);
        } catch (IllegalArgumentException e) {
            throw invalid();
        }
        String[] parts = raw.split(":", -1);
        int version;
        try {
            version = Integer.parseInt(parts[0]);
        } catch (NumberFormatException e) {
            throw invalid();
        }
        if (version != SORT_VERSION) {
            throw ApiException.badRequest("cursor 排序版本已过期，请从头翻页",
                    List.of(new ApiException.Detail("cursor", "stale_sort_version")));
        }
        return parts;
    }

    private static ApiException invalid() {
        return ApiException.badRequest("cursor 无效",
                List.of(new ApiException.Detail("cursor", "invalid_format")));
    }
}
