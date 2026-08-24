package com.modelhub.catalog.service;

import com.modelhub.shared.error.ApiException;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Base64;
import java.util.List;
import java.util.UUID;

/**
 * /repositories 列表 cursor 模式的本地不透明游标（ADR-003）：
 * base64url("2:{score}:{updatedAtEpochMicros}:{publicId}")，sortVersion=2。
 * 与 shared CursorQuery（仅支持单一 long lastKey，供 members/files 等使用）不同，
 * 本编解码承载 keyset 三元组：排序首键打分 + 稳定 tie-breaker（updated_at、publicId）。
 * updatedAt 采用微秒精度（PostgreSQL timestamptz 的分辨率），保证续读无重复/遗漏。
 * 解码失败一律 400 VALIDATION_FAILED，不静默降级。
 */
final class RepoListCursor {

    static final int SORT_VERSION = 2;

    /** keyset 续读位置；updatedAt-desc 无独立打分，score 与 updated_at 微秒值一致（保持格式统一）。 */
    record State(BigDecimal score, OffsetDateTime updatedAt, UUID publicId) {}

    private RepoListCursor() {}

    static String encode(BigDecimal score, OffsetDateTime updatedAt, UUID publicId) {
        String raw = SORT_VERSION + ":" + score.toPlainString() + ":"
                + toEpochMicros(updatedAt.toInstant()) + ":" + publicId;
        return Base64.getUrlEncoder().withoutPadding()
                .encodeToString(raw.getBytes(StandardCharsets.UTF_8));
    }

    static State decode(String cursor) {
        String raw;
        try {
            raw = new String(Base64.getUrlDecoder().decode(cursor), StandardCharsets.UTF_8);
        } catch (IllegalArgumentException e) {
            throw invalid();
        }
        String[] parts = raw.split(":", -1);
        if (parts.length != 4) {
            throw invalid();
        }
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
        try {
            BigDecimal score = new BigDecimal(parts[1]);
            long micros = Long.parseLong(parts[2]);
            UUID publicId = UUID.fromString(parts[3]);
            return new State(score, fromEpochMicros(micros), publicId);
        } catch (IllegalArgumentException e) {
            throw invalid();
        }
    }

    private static ApiException invalid() {
        return ApiException.badRequest("cursor 无效",
                List.of(new ApiException.Detail("cursor", "invalid_format")));
    }

    static long toEpochMicros(Instant t) {
        return Math.addExact(Math.multiplyExact(t.getEpochSecond(), 1_000_000L), t.getNano() / 1_000L);
    }

    private static OffsetDateTime fromEpochMicros(long micros) {
        return OffsetDateTime.ofInstant(Instant.ofEpochSecond(
                Math.floorDiv(micros, 1_000_000L),
                Math.floorMod(micros, 1_000_000L) * 1_000L), ZoneOffset.UTC);
    }
}
