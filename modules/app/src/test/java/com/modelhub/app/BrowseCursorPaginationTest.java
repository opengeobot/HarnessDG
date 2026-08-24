package com.modelhub.app;

import com.fasterxml.jackson.databind.JsonNode;
import com.modelhub.catalog.domain.GitBindingEntity;
import com.modelhub.catalog.repo.GitBindingRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.RestTemplate;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Base64;
import java.util.HexFormat;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * branches/commits/files cursor 分页契约测试（ADR-003 / 04 §6.2，Task 7）：
 * - cursor 为不透明 base64url 值：明文整数 / 坏 base64 / 过期排序版本 / 跨端点误用 → 400；
 * - limit 严格校验：0、101、非整数 → 400（不再静默收敛）；
 * - 稳定数据集翻页无重复、无遗漏；files 翻页遵循 path ASC 排序（keyset 匹配排序）；
 * - 翻尽时 nextCursor 为 JSON null（与 /repositories cursor 模式一致）。
 */
class BrowseCursorPaginationTest extends ArtifactTestSupport {

    @Autowired
    private GitBindingRepository gitBindings;

    /** 明文整数 / 坏 base64 / 过期排序版本 / 跨端点误用（branches 页号 cursor 用在 files）→ 400。 */
    @Test
    void garbageAndStaleCursorsRejected400() throws Exception {
        Session owner = newUser("curbc");
        String repoId = createActiveRepo(owner);
        String token = owner.accessToken();

        // 旧明文整数 cursor（如 "2"）不再是合法格式；坏 base64 同样拒绝
        for (String bad : new String[] {"2", "10", "!!!not-base64!!!"}) {
            assertCursorRejected(token, repoId, "branches", bad);
            assertCursorRejected(token, repoId, "commits", bad);
            assertCursorRejected(token, repoId, "files", bad);
        }

        // 排序版本过期（sortVersion=9）→ 400，要求从头翻页
        String stalePage = b64("9:1");
        assertCursorRejected(token, repoId, "branches", stalePage);
        assertCursorRejected(token, repoId, "commits", stalePage);
        assertCursorRejected(token, repoId, "files", b64("9:" + b64("a.txt") + ":1"));

        // 跨端点误用：branches 页号 cursor（两段载荷）用在 files（要求三段）→ 400
        assertCursorRejected(token, repoId, "files", b64("1:1"));

        // 外层合法但内层载荷非数字 → 400
        assertCursorRejected(token, repoId, "branches", b64("1:abc"));
        assertCursorRejected(token, repoId, "branches", b64("1:-1"));
    }

    /** branches 翻页：Gitea 追加分支后 limit=1 走查无重复/遗漏，翻尽 nextCursor 为 null。 */
    @Test
    void branchesCursorWalkNoDuplicatesOrOmissions() throws Exception {
        Session owner = newUser("curbr");
        String repoId = createActiveRepo(owner);
        // ModelHub 无建分支端点，Gitea 为 Git 真相源：直接在 Gitea 侧追加两个分支凑多页
        createGiteaBranch(repoId, "dev-a");
        createGiteaBranch(repoId, "dev-b");

        Set<String> seen = new LinkedHashSet<>();
        String cursor = null;
        int pages = 0;
        do {
            ResponseEntity<String> resp = browse(owner.accessToken(), repoId, "branches", cursor, "1");
            assertEquals(200, resp.getStatusCode().value(), resp.getBody());
            JsonNode data = dataNode(resp);
            assertTrue(data.path("items").size() <= 1, "limit=1 必须生效");
            for (JsonNode b : data.path("items")) {
                assertTrue(seen.add(b.path("name").asText()), "branches 翻页出现重复分支");
            }
            cursor = nextOf(data);
            if (cursor != null) {
                assertOpaque(cursor);
            }
            assertTrue(++pages < 10, "翻页页数异常（疑似死循环）");
        } while (cursor != null);

        assertEquals(Set.of("main", "dev-a", "dev-b"), seen, "3 个分支必须全部返回（无遗漏）");
    }

    /** commits 翻页：limit=2 走查与 limit=100 全量逐条一致（无重复/遗漏，顺序保持）。 */
    @Test
    void commitsCursorWalkNoDuplicatesOrOmissions() throws Exception {
        Session owner = newUser("curcm");
        String repoId = createActiveRepo(owner);
        uploadTextFile(owner.accessToken(), repoId, "walk.txt", "commit for pagination walk");

        // 全量基准（provisioning 4 提交 + 上传 1 提交，单页 limit=100 可取尽）
        ResponseEntity<String> full = browse(owner.accessToken(), repoId, "commits", null, "100");
        assertEquals(200, full.getStatusCode().value());
        JsonNode fullData = dataNode(full);
        assertTrue(fullData.path("items").size() >= 5, "应至少有 auto_init + 3 个种子文件 + 上传提交");
        assertTrue(fullData.path("nextCursor").isNull(), "全量单页不应再有 nextCursor");
        List<String> expected = new ArrayList<>();
        fullData.path("items").forEach(c -> expected.add(c.path("sha").asText()));

        List<String> walked = new ArrayList<>();
        Set<String> seen = new LinkedHashSet<>();
        String cursor = null;
        int pages = 0;
        do {
            ResponseEntity<String> resp = browse(owner.accessToken(), repoId, "commits", cursor, "2");
            assertEquals(200, resp.getStatusCode().value(), resp.getBody());
            JsonNode data = dataNode(resp);
            assertTrue(data.path("items").size() <= 2, "limit=2 必须生效");
            for (JsonNode c : data.path("items")) {
                assertTrue(seen.add(c.path("sha").asText()), "commits 翻页出现重复提交");
                walked.add(c.path("sha").asText());
            }
            cursor = nextOf(data);
            if (cursor != null) {
                assertOpaque(cursor);
            }
            assertTrue(++pages < 10, "翻页页数异常（疑似死循环）");
        } while (cursor != null);

        assertEquals(expected, walked, "limit=2 走查必须与全量一致（无重复、无遗漏，顺序保持）");
    }

    /** files 翻页：keyset 匹配 path ASC 排序（与上传顺序无关），无重复/遗漏，翻尽为 null。 */
    @Test
    void filesCursorWalkMatchesPathOrderWithoutDuplicatesOrOmissions() throws Exception {
        Session owner = newUser("curfl");
        String repoId = createActiveRepo(owner);
        String token = owner.accessToken();
        // 故意按乱序上传：若 keyset 错用 id 序（上传顺序），分页边界必错
        uploadTextFile(token, repoId, "cur-c.txt", "c");
        uploadTextFile(token, repoId, "cur-a.txt", "a");
        uploadTextFile(token, repoId, "cur-d.txt", "d");
        uploadTextFile(token, repoId, "cur-b.txt", "b");

        List<String> walked = new ArrayList<>();
        Set<String> seen = new LinkedHashSet<>();
        String cursor = null;
        int pages = 0;
        do {
            ResponseEntity<String> resp = browse(token, repoId, "files", cursor, "2");
            assertEquals(200, resp.getStatusCode().value(), resp.getBody());
            JsonNode data = dataNode(resp);
            assertTrue(data.path("items").size() <= 2, "limit=2 必须生效");
            for (JsonNode f : data.path("items")) {
                assertTrue(seen.add(f.path("path").asText()), "files 翻页出现重复文件");
                walked.add(f.path("path").asText());
            }
            cursor = nextOf(data);
            if (cursor != null) {
                assertOpaque(cursor);
            }
            if (pages == 0) {
                // 首页必须是 path 序前两条（cur-a/cur-b），证明排序与 keyset 一致且与上传顺序无关
                assertEquals(List.of("cur-a.txt", "cur-b.txt"), walked, "首页应为 path ASC 前两条");
            }
            assertTrue(++pages < 10, "翻页页数异常（疑似死循环）");
        } while (cursor != null);

        assertEquals(List.of("cur-a.txt", "cur-b.txt", "cur-c.txt", "cur-d.txt"), walked,
                "4 个文件必须按 path ASC 全部返回（无重复、无遗漏）");
    }

    /** limit 严格校验：0 / 101 / 非整数 → 400 VALIDATION_FAILED；缺省与合法值不受影响。 */
    @Test
    void limitStrictlyValidated() throws Exception {
        Session owner = newUser("curlm");
        String repoId = createActiveRepo(owner);
        String token = owner.accessToken();

        for (String endpoint : new String[] {"branches", "commits", "files"}) {
            for (String bad : new String[] {"0", "101", "abc"}) {
                ResponseEntity<String> resp = browse(token, repoId, endpoint, null, bad);
                assertEquals(400, resp.getStatusCode().value(),
                        endpoint + " limit=" + bad + " 应 400: " + resp.getBody());
                assertEquals("VALIDATION_FAILED", errorCode(resp));
            }
            assertEquals(200, browse(token, repoId, endpoint, null, null).getStatusCode().value(),
                    endpoint + " 缺省 limit 应可用");
            assertEquals(200, browse(token, repoId, endpoint, null, "1").getStatusCode().value(),
                    endpoint + " limit=1 应可用");
        }
    }

    // ---------- 工具 ----------

    private ResponseEntity<String> browse(String token, String repoId, String endpoint,
                                          String cursor, String limit) {
        StringBuilder uri = new StringBuilder("/api/v1/repositories/")
                .append(repoId).append('/').append(endpoint);
        String sep = "?";
        if (cursor != null) {
            uri.append(sep).append("cursor=").append(cursor);
            sep = "&";
        }
        if (limit != null) {
            uri.append(sep).append("limit=").append(limit);
        }
        return rest.exchange(uri.toString(), HttpMethod.GET, new HttpEntity<>(bearer(token)), String.class);
    }

    private void assertCursorRejected(String token, String repoId, String endpoint, String cursor) {
        ResponseEntity<String> resp = browse(token, repoId, endpoint, cursor, null);
        assertEquals(400, resp.getStatusCode().value(),
                endpoint + " cursor=" + cursor + " 应 400: " + resp.getBody());
        assertEquals("VALIDATION_FAILED", errorCode(resp));
    }

    /** nextCursor 存在时必须非空、非明文整数（不透明 base64url）。 */
    private static void assertOpaque(String cursor) {
        assertFalse(cursor.isBlank(), "nextCursor 存在时不得为空串");
        assertFalse(cursor.matches("\\d+"), "cursor 不得为明文整数（旧格式）: " + cursor);
    }

    private static String nextOf(JsonNode data) {
        JsonNode next = data.path("nextCursor");
        assertTrue(!next.isMissingNode(), "cursor 模式必须携带 nextCursor 字段");
        return next.isNull() ? null : next.asText();
    }

    private static String b64(String raw) {
        return Base64.getUrlEncoder().withoutPadding().encodeToString(raw.getBytes(StandardCharsets.UTF_8));
    }

    /** 以 Gitea 管理员直连 Gitea API 从 main 建分支（测试专用，凑多分支分页数据）。 */
    private void createGiteaBranch(String repoId, String branchName) {
        Long internalId = repositoryRepo.findByPublicId(UUID.fromString(repoId)).orElseThrow().getId();
        GitBindingEntity binding = gitBindings.findByRepositoryId(internalId).orElseThrow();
        String base = "http://" + GITEA.getHost() + ":" + GITEA.getMappedPort(3000);
        HttpHeaders h = new HttpHeaders();
        h.setContentType(MediaType.APPLICATION_JSON);
        h.setBasicAuth("modelhub", "ModelHub-Root-1x");
        ResponseEntity<String> resp = new RestTemplate().postForEntity(
                base + "/api/v1/repos/" + binding.getExternalNamespace() + "/" + binding.getExternalName()
                        + "/branches",
                new HttpEntity<>(java.util.Map.of("new_branch_name", branchName, "old_ref_name", "main"), h),
                String.class);
        assertTrue(resp.getStatusCode().is2xxSuccessful(),
                "Gitea 建分支失败: " + resp.getStatusCode() + " " + resp.getBody());
    }

    /** 上传一个小文本文件（git source）并等待 active。 */
    private void uploadTextFile(String token, String repoId, String path, String text) throws Exception {
        byte[] bytes = text.getBytes(StandardCharsets.UTF_8);
        JsonNode up = initiateUpload(token, repoId, "main", headSha(token, repoId), path,
                bytes.length, sha256Hex(bytes), "text/plain");
        String uploadId = up.path("id").asText();
        awaitUploadStatus(token, uploadId, "uploading");
        putPart(partUrls(token, uploadId, List.of(1)).get(0).path("url").asText(), bytes);
        completeUpload(token, uploadId);
        awaitUploadStatus(token, uploadId, "completed");
        awaitFileStatus(token, repoId, path, "active");
    }

    private static String sha256Hex(byte[] data) throws Exception {
        return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(data));
    }
}
