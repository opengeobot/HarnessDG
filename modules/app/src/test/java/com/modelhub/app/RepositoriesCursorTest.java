package com.modelhub.app;

import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;

import java.nio.charset.StandardCharsets;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Base64;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * /repositories cursor 模式集成测试（ADR-003 / Task 3）：
 * - cursor/limit 参数不再静默退化为 page 1 size 12（原 PageQuery.from 缺省行为）；
 * - keyset 行值比较续读：无重复、无遗漏，且跨页全局有序（score DESC, updated_at DESC, publicId DESC）；
 * - 非法 cursor / 过期排序版本 cursor / 混用 page+cursor / 非法 limit 均 400；
 * - page 模式响应形状保持不变（items/total/page/pageSize，无 nextCursor）。
 */
class RepositoriesCursorTest extends CatalogTestSupport {

    @Test
    void cursorModeWalksUpdatedAtDescWithoutDuplicatesOrOmissions() throws Exception {
        Session owner = newUser("curupd");
        String ns = userNamespaceId(owner);
        String prefix = unique("curupd");
        java.util.Set<String> created = new HashSet<>();
        for (int i = 0; i < 7; i++) {
            String repoId = dataNode(createRepo(owner, ns, prefix + "-" + i, "public", null, null))
                    .path("id").asText();
            awaitLifecycleHttp(owner.accessToken(), repoId, "active");
            created.add(repoId);
        }

        java.util.Set<String> seen = new HashSet<>();
        List<String> walked = new ArrayList<>();
        List<OffsetDateTime> updatedAtSeq = new ArrayList<>();
        String cursor = null;
        boolean firstPage = true;
        int pages = 0;
        do {
            String uri = "/api/v1/repositories?keyword=" + prefix + "&sort=updatedAt-desc&limit=3"
                    + (cursor == null ? "" : "&cursor=" + cursor);
            ResponseEntity<String> resp = rest.exchange(uri, HttpMethod.GET,
                    new HttpEntity<>(ipHeaders()), String.class);
            assertEquals(200, resp.getStatusCode().value());
            JsonNode data = dataNode(resp);
            // cursor 模式形状：仅 items + nextCursor，不携带 page 模式字段（证明未静默降级为 page 1/12）
            assertTrue(data.path("total").isMissingNode(), "cursor 模式不应返回 total");
            assertTrue(data.path("page").isMissingNode(), "cursor 模式不应返回 page");
            assertTrue(data.path("pageSize").isMissingNode(), "cursor 模式不应返回 pageSize");
            assertTrue(data.path("items").size() <= 3, "limit=3 必须生效");
            for (JsonNode item : data.path("items")) {
                assertTrue(seen.add(item.path("id").asText()), "cursor 翻页出现重复条目");
                walked.add(item.path("id").asText());
                updatedAtSeq.add(OffsetDateTime.parse(item.path("updatedAt").asText()));
            }
            cursor = data.path("nextCursor").isNull() ? null : data.path("nextCursor").asText();
            if (cursor != null) {
                assertFalse(cursor.isBlank(), "nextCursor 存在时不得为空串");
            }
            if (firstPage) {
                assertEquals(3, data.path("items").size(), "首屏应精确返回 limit=3 条（而非 12 条）");
                assertNotNull(cursor, "7 > 3 时首屏必须携带 nextCursor");
                firstPage = false;
            }
            assertTrue(++pages < 10, "翻页页数异常（疑似死循环）");
        } while (cursor != null);

        assertEquals(7, walked.size(), "7 个可见仓库必须全部返回（无遗漏）");
        assertEquals(created, seen);
        // updatedAt-desc 跨页全局降序
        for (int i = 1; i < updatedAtSeq.size(); i++) {
            assertFalse(updatedAtSeq.get(i).isAfter(updatedAtSeq.get(i - 1)),
                    "updatedAt 必须跨页全局降序");
        }
    }

    @Test
    void cursorModeWalksLikesDescWithScoreTiesAndTiebreaker() throws Exception {
        Session owner = newUser("curlk");
        String ns = userNamespaceId(owner);
        String prefix = unique("curlk");
        Session[] likers = {newUser("curlka"), newUser("curlkb"), newUser("curlkc")};
        // likes 分布 3,3,2,1,0,0,0：既有不同打分也有并列打分，覆盖 score 比较 + tie-breaker 组合 keyset
        int[] likesPlan = {3, 3, 2, 1, 0, 0, 0};
        Map<String, Integer> likesById = new LinkedHashMap<>();
        for (int i = 0; i < 7; i++) {
            String repoId = dataNode(createRepo(owner, ns, prefix + "-" + i, "public", null, null))
                    .path("id").asText();
            awaitLifecycleHttp(owner.accessToken(), repoId, "active");
            likesById.put(repoId, likesPlan[i]);
        }
        for (Map.Entry<String, Integer> e : likesById.entrySet()) {
            for (int u = 0; u < e.getValue(); u++) {
                ResponseEntity<String> liked = postJson(likers[u].accessToken(),
                        "/api/v1/repositories/" + e.getKey() + "/likes", Map.of(), null);
                assertEquals(200, liked.getStatusCode().value(), "点赞失败: " + liked.getBody());
            }
        }
        // 等待 likes 投影收敛（Outbox 异步重算；事实表幂等，收敛后走查期间稳定）
        for (Map.Entry<String, Integer> e : likesById.entrySet()) {
            awaitStats(owner.accessToken(), e.getKey(), "likes", e.getValue());
        }

        List<String> walked = new ArrayList<>();
        List<OffsetDateTime> updatedAtSeq = new ArrayList<>();
        String cursor = null;
        int pages = 0;
        do {
            String uri = "/api/v1/repositories?keyword=" + prefix + "&sort=likes-desc&limit=2"
                    + (cursor == null ? "" : "&cursor=" + cursor);
            ResponseEntity<String> resp = rest.exchange(uri, HttpMethod.GET,
                    new HttpEntity<>(ipHeaders()), String.class);
            assertEquals(200, resp.getStatusCode().value());
            JsonNode data = dataNode(resp);
            assertTrue(data.path("items").size() <= 2, "limit=2 必须生效");
            for (JsonNode item : data.path("items")) {
                walked.add(item.path("id").asText());
                updatedAtSeq.add(OffsetDateTime.parse(item.path("updatedAt").asText()));
            }
            cursor = data.path("nextCursor").isNull() ? null : data.path("nextCursor").asText();
            assertTrue(++pages < 10, "翻页页数异常（疑似死循环）");
        } while (cursor != null);

        assertEquals(7, walked.size(), "7 个可见仓库必须全部返回（无重复、无遗漏）");
        assertEquals(likesById.keySet(), new HashSet<>(walked));
        // 跨页全局有序：likes 非增；likes 并列时 updatedAt 非增（稳定 tie-breaker）
        for (int i = 1; i < walked.size(); i++) {
            int prevLikes = likesById.get(walked.get(i - 1));
            int curLikes = likesById.get(walked.get(i));
            assertTrue(prevLikes >= curLikes, "likes 必须跨页全局非增: " + walked);
            if (prevLikes == curLikes) {
                assertFalse(updatedAtSeq.get(i).isAfter(updatedAtSeq.get(i - 1)),
                        "likes 并列时 updatedAt 必须降序: " + walked);
            }
        }
    }

    @Test
    void invalidCursorRejected400() {
        ResponseEntity<String> resp = rest.exchange("/api/v1/repositories?cursor=!!!not-base64!!!",
                HttpMethod.GET, new HttpEntity<>(ipHeaders()), String.class);
        assertEquals(400, resp.getStatusCode().value());
        assertEquals("VALIDATION_FAILED", errorCode(resp));

        // 可解码但排序版本过期（sortVersion=1）的 cursor → 400，要求从头翻页
        String stale = Base64.getUrlEncoder().withoutPadding()
                .encodeToString("1:0:0:00000000-0000-0000-0000-000000000000"
                        .getBytes(StandardCharsets.UTF_8));
        ResponseEntity<String> staleResp = rest.exchange("/api/v1/repositories?cursor=" + stale,
                HttpMethod.GET, new HttpEntity<>(ipHeaders()), String.class);
        assertEquals(400, staleResp.getStatusCode().value());
        assertEquals("VALIDATION_FAILED", errorCode(staleResp));
    }

    @Test
    void mixingPageAndCursorParamsRejected400() {
        ResponseEntity<String> pageAndLimit = rest.exchange("/api/v1/repositories?page=1&limit=5",
                HttpMethod.GET, new HttpEntity<>(ipHeaders()), String.class);
        assertEquals(400, pageAndLimit.getStatusCode().value());
        assertEquals("VALIDATION_FAILED", errorCode(pageAndLimit));

        ResponseEntity<String> pageSizeAndCursor = rest.exchange(
                "/api/v1/repositories?pageSize=5&cursor=anything", HttpMethod.GET,
                new HttpEntity<>(ipHeaders()), String.class);
        assertEquals(400, pageSizeAndCursor.getStatusCode().value());
        assertEquals("VALIDATION_FAILED", errorCode(pageSizeAndCursor));
    }

    @Test
    void cursorModeLimitValidationRejected() {
        for (String bad : new String[]{"0", "101", "abc"}) {
            ResponseEntity<String> resp = rest.exchange("/api/v1/repositories?limit=" + bad,
                    HttpMethod.GET, new HttpEntity<>(ipHeaders()), String.class);
            assertEquals(400, resp.getStatusCode().value(), "limit=" + bad + " 应 400");
            assertEquals("VALIDATION_FAILED", errorCode(resp));
        }
    }

    @Test
    void pageModeResponseShapeUnchanged() throws Exception {
        Session owner = newUser("curpg");
        String ns = userNamespaceId(owner);
        String prefix = unique("curpg");
        for (int i = 0; i < 3; i++) {
            String repoId = dataNode(createRepo(owner, ns, prefix + "-" + i, "public", null, null))
                    .path("id").asText();
            awaitLifecycleHttp(owner.accessToken(), repoId, "active");
        }
        ResponseEntity<String> resp = rest.exchange(
                "/api/v1/repositories?keyword=" + prefix + "&pageSize=2&page=1",
                HttpMethod.GET, new HttpEntity<>(ipHeaders()), String.class);
        assertEquals(200, resp.getStatusCode().value());
        JsonNode data = dataNode(resp);
        assertEquals(3, data.path("total").asLong());
        assertEquals(1, data.path("page").asLong());
        assertEquals(2, data.path("pageSize").asLong());
        assertEquals(2, data.path("items").size());
        assertTrue(data.path("nextCursor").isMissingNode(), "page 模式不应出现 nextCursor");
    }
}
