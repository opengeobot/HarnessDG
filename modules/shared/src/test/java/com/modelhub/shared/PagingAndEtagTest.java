package com.modelhub.shared;

import com.modelhub.shared.error.ApiException;
import com.modelhub.shared.paging.CursorQuery;
import com.modelhub.shared.paging.PageQuery;
import com.modelhub.shared.web.ETags;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * shared 基座纯单元测试：分页双模式互斥与边界（04 §6）、ETag 条件更新（04 §10）。
 */
class PagingAndEtagTest {

    @Test
    void page_query_defaults_and_bounds() {
        PageQuery def = PageQuery.from(Map.of());
        assertThat(def.page()).isEqualTo(1);
        assertThat(def.pageSize()).isEqualTo(12);

        assertThat(PageQuery.from(Map.of("page", "3", "pageSize", "20")).offset()).isEqualTo(40);

        assertThatThrownBy(() -> PageQuery.from(Map.of("page", "0")))
                .isInstanceOf(ApiException.class);
        assertThatThrownBy(() -> PageQuery.from(Map.of("pageSize", "101")))
                .isInstanceOf(ApiException.class);
        assertThatThrownBy(() -> PageQuery.from(Map.of("page", "abc")))
                .isInstanceOf(ApiException.class);
    }

    @Test
    void pagination_modes_are_mutually_exclusive() {
        assertThatThrownBy(() -> PageQuery.from(Map.of("page", "1", "cursor", "e")))
                .isInstanceOf(ApiException.class)
                .hasMessageContaining("互斥");
        assertThatThrownBy(() -> CursorQuery.from(Map.of("limit", "5", "pageSize", "5")))
                .isInstanceOf(ApiException.class)
                .hasMessageContaining("互斥");
    }

    @Test
    void cursor_roundtrip_and_invalid_values() {
        String encoded = CursorQuery.encode(42L);
        CursorQuery q = CursorQuery.from(Map.of("cursor", encoded, "limit", "10"));
        assertThat(q.lastKey()).isEqualTo(42L);
        assertThat(q.limit()).isEqualTo(10);

        assertThatThrownBy(() -> CursorQuery.from(Map.of("cursor", "!!!")))
                .isInstanceOf(ApiException.class);
        assertThatThrownBy(() -> CursorQuery.from(Map.of("limit", "101")))
                .isInstanceOf(ApiException.class);
        // 排序版本不匹配 → 要求从头翻页
        String staleVersion = java.util.Base64.getUrlEncoder().withoutPadding()
                .encodeToString("9:1".getBytes(java.nio.charset.StandardCharsets.UTF_8));
        assertThatThrownBy(() -> CursorQuery.from(Map.of("cursor", staleVersion)))
                .isInstanceOf(ApiException.class)
                .hasMessageContaining("排序版本");
    }

    @Test
    void etag_if_match_semantics() {
        String etag = ETags.ofVersion(7);
        assertThat(etag).startsWith("\"").endsWith("\"");

        assertThatThrownBy(() -> ETags.requireMatch(null, etag, "资源"))
                .isInstanceOf(ApiException.class);
        assertThatThrownBy(() -> ETags.requireMatch("\"other\"", etag, "资源"))
                .isInstanceOf(ApiException.class);
        ETags.requireMatch(etag, etag, "资源");
        ETags.requireMatch("*", etag, "资源");
        ETags.requireMatch("W/" + etag, etag, "资源");
    }
}
