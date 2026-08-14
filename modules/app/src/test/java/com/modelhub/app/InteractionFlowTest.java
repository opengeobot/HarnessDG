package com.modelhub.app;

import com.fasterxml.jackson.databind.JsonNode;
import com.modelhub.catalog.service.StatsRebuildService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 互动与统计投影集成测试（03 §6 / 04 §5 / 06 §7）：
 * likes/favorites 幂等与并发唯一（INTERACT-001）、feedbacks 分页与防枚举、
 * /me/repositories tabs（ME-001）、visit 30 分钟窗口去重与 downloads/fileCount 收敛、
 * stats 对账重建（INTERACT-002）。stats 收敛一律轮询等待，不用 sleep 固定值。
 */
class InteractionFlowTest extends ArtifactTestSupport {

    @Autowired
    private StatsRebuildService statsRebuild;

    @Autowired
    private JdbcTemplate jdbc;

    // ---------- 点赞/收藏 ----------

    @Test
    void likeIdempotentAndConcurrentUnique() throws Exception {
        Session owner = newUser("likeown");
        String repoId = dataNode(createRepo(owner, userNamespaceId(owner), unique("likerepo"),
                "public", null, null)).path("id").asText();
        awaitLifecycleHttp(owner.accessToken(), repoId, "active");

        // POST 两次均 200 active:true（幂等，禁止 Toggle）
        assertEquals(200, postLike(owner.accessToken(), repoId).getStatusCode().value());
        ResponseEntity<String> again = postLike(owner.accessToken(), repoId);
        assertEquals(200, again.getStatusCode().value());
        assertTrue(dataNode(again).path("active").asBoolean());
        awaitStats(owner.accessToken(), repoId, "likes", 1);

        // DELETE 两次均 204 且收敛 0
        assertEquals(204, deleteLike(owner.accessToken(), repoId).getStatusCode().value());
        assertEquals(204, deleteLike(owner.accessToken(), repoId).getStatusCode().value());
        awaitStats(owner.accessToken(), repoId, "likes", 0);

        // 两线程并发 POST：均无异常且最终收敛 1（UNIQUE 兜底，INTERACT-001）
        List<Exception> errors = Collections.synchronizedList(new ArrayList<>());
        List<Thread> threads = new ArrayList<>();
        for (int i = 0; i < 2; i++) {
            Thread t = new Thread(() -> {
                try {
                    postLike(owner.accessToken(), repoId);
                } catch (Exception e) {
                    errors.add(e);
                }
            });
            threads.add(t);
            t.start();
        }
        for (Thread t : threads) {
            t.join();
        }
        assertTrue(errors.isEmpty(), "并发点赞不得异常: " + errors);
        awaitStats(owner.accessToken(), repoId, "likes", 1);
    }

    @Test
    void favoriteFlow() throws Exception {
        Session owner = newUser("favown");
        String repoId = createActiveRepo(owner);

        ResponseEntity<String> fav = postFavorite(owner.accessToken(), repoId);
        assertEquals(200, fav.getStatusCode().value());
        assertTrue(dataNode(fav).path("active").asBoolean());
        assertTrue(meTabIds(owner.accessToken(), "favorites").contains(repoId));

        assertEquals(204, deleteFavorite(owner.accessToken(), repoId).getStatusCode().value());
        assertFalse(meTabIds(owner.accessToken(), "favorites").contains(repoId));
    }

    // ---------- 反馈 ----------

    @Test
    void feedbacksCreateAndPage() throws Exception {
        Session owner = newUser("fbkown");
        String repoId = createActiveRepo(owner);

        ResponseEntity<String> created = postJson(owner.accessToken(),
                "/api/v1/repositories/" + repoId + "/feedbacks",
                Map.of("content", "很好用的模型"), null);
        assertEquals(201, created.getStatusCode().value());
        JsonNode fb = dataNode(created);
        assertEquals(owner.username(), fb.path("author").asText());
        assertTrue(fb.path("id").isTextual());

        // 匿名 GET 分页可读
        ResponseEntity<String> page = rest.exchange("/api/v1/repositories/" + repoId + "/feedbacks?limit=5",
                HttpMethod.GET, new HttpEntity<>(ipHeaders()), String.class);
        assertEquals(200, page.getStatusCode().value());
        JsonNode items = dataNode(page).path("items");
        assertEquals(1, items.size());
        assertEquals("很好用的模型", items.get(0).path("content").asText());
        assertEquals(owner.username(), items.get(0).path("author").asText());

        // 超长 content（10001）→ 422 VALIDATION_FAILED（GlobalExceptionHandler 校验映射）
        ResponseEntity<String> tooLong = postJson(owner.accessToken(),
                "/api/v1/repositories/" + repoId + "/feedbacks",
                Map.of("content", "x".repeat(10001)), null);
        assertEquals(422, tooLong.getStatusCode().value());
        assertEquals("VALIDATION_FAILED", errorCode(tooLong));

        // 陌生人 GET private 仓库反馈 → 404（防枚举）
        Session stranger = newUser("fbkstranger");
        String privRepo = dataNode(createRepo(owner, userNamespaceId(owner), unique("privfbk"),
                "private", null, null)).path("id").asText();
        awaitLifecycleHttp(owner.accessToken(), privRepo, "active");
        postJson(owner.accessToken(), "/api/v1/repositories/" + privRepo + "/feedbacks",
                Map.of("content", "内部反馈"), null);
        ResponseEntity<String> denied = rest.exchange("/api/v1/repositories/" + privRepo + "/feedbacks",
                HttpMethod.GET, new HttpEntity<>(bearer(stranger.accessToken())), String.class);
        assertEquals(404, denied.getStatusCode().value());
    }

    // ---------- /me/repositories ----------

    @Test
    void meRepositoriesTabs() throws Exception {
        Session owner = newUser("meown");
        Session other = newUser("meother");
        String ownRepo = createActiveRepo(owner);
        String otherRepo = createActiveRepo(other);

        // created：仅本人仓库
        Set<String> created = meTabIds(owner.accessToken(), "created");
        assertTrue(created.contains(ownRepo));
        assertFalse(created.contains(otherRepo));

        // 非法 tab → 400
        ResponseEntity<String> badTab = rest.exchange("/api/v1/me/repositories?tab=starred",
                HttpMethod.GET, new HttpEntity<>(bearer(owner.accessToken())), String.class);
        assertEquals(400, badTab.getStatusCode().value());

        // owner 点赞 otherRepo → tab=likes 可见
        assertEquals(200, postLike(owner.accessToken(), otherRepo).getStatusCode().value());
        assertTrue(meTabIds(owner.accessToken(), "likes").contains(otherRepo));

        // otherRepo 改为 private → 他人 tab=likes 不可见（ME-001）；仓库主自赞后可见
        long version = dataNode(repoDetail(other.accessToken(), otherRepo)).path("version").asLong();
        ResponseEntity<String> patched = patchRepo(other.accessToken(), otherRepo,
                Map.of("visibility", "private"), etagOfVersion(version));
        assertEquals(200, patched.getStatusCode().value());
        assertFalse(meTabIds(owner.accessToken(), "likes").contains(otherRepo));
        assertEquals(200, postLike(other.accessToken(), otherRepo).getStatusCode().value());
        assertTrue(meTabIds(other.accessToken(), "likes").contains(otherRepo));
    }

    // ---------- visit 去重 / fileCount / downloads ----------

    @Test
    void visitDedupAndDownloadsFileCount() throws Exception {
        Session owner = newUser("visitown");
        String repoId = createActiveRepo(owner);
        // createActiveRepo 的 awaitLifecycleHttp 已记录一次 owner visit

        // 同一用户重复详情 → 收敛 1（30 分钟窗口 + 身份摘要去重）
        repoDetail(owner.accessToken(), repoId);
        repoDetail(owner.accessToken(), repoId);
        awaitStats(owner.accessToken(), repoId, "visits", 1);

        // 匿名同 IP 重复详情 → 仅 +1（HMAC 摘要去重）
        HttpHeaders anon = new HttpHeaders();
        anon.add("X-Test-Client-Ip", "10.9.7.7");
        rest.exchange("/api/v1/repositories/" + repoId, HttpMethod.GET, new HttpEntity<>(anon), String.class);
        rest.exchange("/api/v1/repositories/" + repoId, HttpMethod.GET, new HttpEntity<>(anon), String.class);
        awaitStats(owner.accessToken(), repoId, "visits", 2);

        // 上传完成 → fileCount 收敛 1
        byte[] content = "visit me".getBytes(StandardCharsets.UTF_8);
        JsonNode up = initiateUpload(owner.accessToken(), repoId, "main",
                headSha(owner.accessToken(), repoId), "f.txt", content.length,
                sha256Hex(content), "text/plain");
        String uploadId = up.path("id").asText();
        awaitUploadStatus(owner.accessToken(), uploadId, "uploading");
        putPart(partUrls(owner.accessToken(), uploadId, List.of(1)).get(0).path("url").asText(), content);
        completeUpload(owner.accessToken(), uploadId);
        String fileId = awaitFileStatus(owner.accessToken(), repoId, "f.txt", "active").path("id").asText();
        awaitStats(owner.accessToken(), repoId, "fileCount", 1);

        // 签发下载会话 → downloads 收敛 1；同 Idempotency-Key 重放不重复计数
        String key = UUID.randomUUID().toString();
        createDownloadSession(owner.accessToken(), repoId, fileId, key);
        awaitStats(owner.accessToken(), repoId, "downloads", 1);
        createDownloadSession(owner.accessToken(), repoId, fileId, key);
        Thread.sleep(1_000); // 数个 poller 周期，确认重放未产生新计数
        assertEquals(1, statsValue(owner.accessToken(), repoId, "downloads"));
    }

    // ---------- stats 对账重建 ----------

    @Test
    void statsRebuildRestoresFromFactTables() throws Exception {
        Session owner = newUser("rebuild");
        String repoId = createActiveRepo(owner);
        assertEquals(200, postLike(owner.accessToken(), repoId).getStatusCode().value());
        awaitStats(owner.accessToken(), repoId, "likes", 1);

        // 置脏 stats 行（模拟计数漂移）
        int updated = jdbc.update("UPDATE repository_stats SET likes = 99, favorites = 42, visits = 7 "
                        + "WHERE repository_id = (SELECT id FROM repositories WHERE public_id = ?)",
                UUID.fromString(repoId));
        assertEquals(1, updated);
        assertEquals(99, statsValue(owner.accessToken(), repoId, "likes"));

        // 对账重建：以强事实表为准恢复（INTERACT-002）
        statsRebuild.rebuild(UUID.fromString(repoId));
        assertEquals(1, statsValue(owner.accessToken(), repoId, "likes"));
        assertEquals(0, statsValue(owner.accessToken(), repoId, "favorites"));
    }

    // ---------- 助手 ----------

    private ResponseEntity<String> postLike(String token, String repoId) {
        return postJson(token, "/api/v1/repositories/" + repoId + "/likes", Map.of(), null);
    }

    private ResponseEntity<String> deleteLike(String token, String repoId) {
        return rest.exchange("/api/v1/repositories/" + repoId + "/likes", HttpMethod.DELETE,
                new HttpEntity<>(bearer(token)), String.class);
    }

    private ResponseEntity<String> postFavorite(String token, String repoId) {
        return postJson(token, "/api/v1/repositories/" + repoId + "/favorite", Map.of(), null);
    }

    private ResponseEntity<String> deleteFavorite(String token, String repoId) {
        return rest.exchange("/api/v1/repositories/" + repoId + "/favorite", HttpMethod.DELETE,
                new HttpEntity<>(bearer(token)), String.class);
    }

    private Set<String> meTabIds(String token, String tab) {
        ResponseEntity<String> resp = rest.exchange("/api/v1/me/repositories?tab=" + tab,
                HttpMethod.GET, new HttpEntity<>(bearer(token)), String.class);
        assertEquals(200, resp.getStatusCode().value(), "tab=" + tab + " 应可读: " + resp.getBody());
        return StreamSupport.stream(dataNode(resp).path("items").spliterator(), false)
                .map(n -> n.path("id").asText())
                .collect(Collectors.toSet());
    }

    static String sha256Hex(byte[] data) throws Exception {
        return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(data));
    }
}
