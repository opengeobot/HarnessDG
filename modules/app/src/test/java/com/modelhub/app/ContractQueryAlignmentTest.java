package com.modelhub.app;

import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 契约查询参数对齐集成测试（Task 6）：
 * files ref 解析（分支名/commit sha/互斥/未知 ref）、/me/access-requests status 枚举校验、
 * resource-types ResourceTypeListEnvelope（data.items）与未声明 {typeKey} 端点下线。
 */
class ContractQueryAlignmentTest extends ArtifactTestSupport {

    /** ref=分支名与 branch=分支名等价；ref=sha（来自 commits 端点）返回该提交时刻的文件集。 */
    @Test
    void filesRefResolvesBranchAndCommitSha() throws Exception {
        Session owner = newUser("refq");
        String repoId = createActiveRepo(owner);
        String token = owner.accessToken();

        String h1 = uploadTextFile(token, repoId, "ref-a.txt", "aaa");
        String h2 = uploadTextFile(token, repoId, "ref-b.txt", "bbb");
        assertNotEquals(h1, h2);

        // ref=<分支名> 与 branch=<分支名> 等价
        ResponseEntity<String> byBranch = rest.exchange(
                "/api/v1/repositories/" + repoId + "/files?branch=main",
                HttpMethod.GET, new HttpEntity<>(bearer(token)), String.class);
        ResponseEntity<String> byRefBranch = rest.exchange(
                "/api/v1/repositories/" + repoId + "/files?ref=main",
                HttpMethod.GET, new HttpEntity<>(bearer(token)), String.class);
        assertEquals(200, byBranch.getStatusCode().value());
        assertEquals(200, byRefBranch.getStatusCode().value());
        assertEquals(pathsOf(byBranch), pathsOf(byRefBranch));
        assertEquals(dataNode(byBranch).path("resolvedCommitSha").asText(),
                dataNode(byRefBranch).path("resolvedCommitSha").asText());

        // commits 端点取 head sha：ref=sha 返回该提交时刻的文件集（两个文件均已存在）
        ResponseEntity<String> commitsResp = rest.exchange(
                "/api/v1/repositories/" + repoId + "/commits",
                HttpMethod.GET, new HttpEntity<>(bearer(token)), String.class);
        assertEquals(200, commitsResp.getStatusCode().value());
        String headSha = dataNode(commitsResp).path("items").get(0).path("sha").asText();
        assertEquals(h2, headSha);
        ResponseEntity<String> bySha = rest.exchange(
                "/api/v1/repositories/" + repoId + "/files?ref=" + headSha,
                HttpMethod.GET, new HttpEntity<>(bearer(token)), String.class);
        assertEquals(200, bySha.getStatusCode().value());
        assertEquals(headSha, dataNode(bySha).path("resolvedCommitSha").asText());
        assertEquals(Set.of("ref-a.txt", "ref-b.txt"), pathsOf(bySha));

        // 历史中间提交：ref=第一个文件提交 → 仅该提交时刻已存在的文件（ref-b.txt 尚未提交）
        ResponseEntity<String> atH1 = rest.exchange(
                "/api/v1/repositories/" + repoId + "/files?ref=" + h1,
                HttpMethod.GET, new HttpEntity<>(bearer(token)), String.class);
        assertEquals(200, atH1.getStatusCode().value());
        assertEquals(h1, dataNode(atH1).path("resolvedCommitSha").asText());
        assertEquals(Set.of("ref-a.txt"), pathsOf(atH1));

        // branch 与 ref 互斥 → 400 VALIDATION_FAILED
        ResponseEntity<String> both = rest.exchange(
                "/api/v1/repositories/" + repoId + "/files?branch=main&ref=main",
                HttpMethod.GET, new HttpEntity<>(bearer(token)), String.class);
        assertEquals(400, both.getStatusCode().value());
        assertEquals("VALIDATION_FAILED", errorCode(both));

        // 垃圾 ref 与垃圾分支行为一致：404 RESOURCE_NOT_FOUND
        ResponseEntity<String> badBranch = rest.exchange(
                "/api/v1/repositories/" + repoId + "/files?branch=no-such-branch",
                HttpMethod.GET, new HttpEntity<>(bearer(token)), String.class);
        ResponseEntity<String> badRef = rest.exchange(
                "/api/v1/repositories/" + repoId + "/files?ref=no-such-ref",
                HttpMethod.GET, new HttpEntity<>(bearer(token)), String.class);
        assertEquals(404, badBranch.getStatusCode().value());
        assertEquals(404, badRef.getStatusCode().value());
        assertEquals("RESOURCE_NOT_FOUND", errorCode(badRef));

        // 形似 sha 但不存在的 ref → 同样 404
        ResponseEntity<String> unknownSha = rest.exchange(
                "/api/v1/repositories/" + repoId + "/files?ref=" + "0".repeat(40),
                HttpMethod.GET, new HttpEntity<>(bearer(token)), String.class);
        assertEquals(404, unknownSha.getStatusCode().value());
        assertEquals("RESOURCE_NOT_FOUND", errorCode(unknownSha));
    }

    /** /me/access-requests：合法 status 过滤生效，非法值（含已移除的 withdrawn）→ 422。 */
    @Test
    void meAccessRequestsStatusEnumValidated() throws Exception {
        Session owner = newUser("stvown");
        Session applicant = newUser("stvreq");
        String ns = userNamespaceId(owner);
        String repoId = dataNode(createRepo(owner, ns, unique("Stv"), "public", true, null))
                .path("id").asText();
        awaitLifecycleHttp(owner.accessToken(), repoId, "active");

        ResponseEntity<String> req = postJson(applicant.accessToken(),
                "/api/v1/repositories/" + repoId + "/access-requests",
                Map.of("reason", "需要访问该模型"), null);
        assertEquals(201, req.getStatusCode().value());

        ResponseEntity<String> pending = rest.exchange("/api/v1/me/access-requests?status=pending",
                HttpMethod.GET, new HttpEntity<>(bearer(applicant.accessToken())), String.class);
        assertEquals(200, pending.getStatusCode().value());
        assertTrue(dataNode(pending).path("items").size() >= 1, "pending 过滤应至少返回刚创建的申请");
        for (JsonNode item : dataNode(pending).path("items")) {
            assertEquals("pending", item.path("status").asText());
        }

        // 非法枚举（含已从契约移除的 withdrawn）→ 422 VALIDATION_FAILED
        for (String bad : new String[] {"garbage", "withdrawn"}) {
            ResponseEntity<String> resp = rest.exchange("/api/v1/me/access-requests?status=" + bad,
                    HttpMethod.GET, new HttpEntity<>(bearer(applicant.accessToken())), String.class);
            assertEquals(422, resp.getStatusCode().value(), "status=" + bad);
            assertEquals("VALIDATION_FAILED", errorCode(resp));
        }
    }

    /** /resource-types 契约 ResourceTypeListEnvelope（data.items）；未声明的 {typeKey} 端点已下线 → 404。 */
    @Test
    void resourceTypesEnvelopeShapeAndTypeKeyRemoved() {
        ResponseEntity<String> types = rest.exchange("/api/v1/resource-types", HttpMethod.GET,
                new HttpEntity<>(ipHeaders()), String.class);
        assertEquals(200, types.getStatusCode().value());
        JsonNode data = dataNode(types);
        assertTrue(data.isObject(), "契约 ResourceTypeListEnvelope：data 应为对象");
        JsonNode items = data.path("items");
        assertTrue(items.isArray() && items.size() >= 3, "data.items 应至少包含 model/dataset/studio");
        for (JsonNode t : items) {
            assertFalse(t.path("typeKey").asText().isBlank());
        }

        // 契约未声明 GET /resource-types/{typeKey}（仅 /schema 变体在契约内）→ 404
        ResponseEntity<String> removed = rest.exchange("/api/v1/resource-types/model", HttpMethod.GET,
                new HttpEntity<>(ipHeaders()), String.class);
        assertEquals(404, removed.getStatusCode().value());
        assertEquals("RESOURCE_NOT_FOUND", errorCode(removed));

        // 契约内 /schema 端点不受影响
        ResponseEntity<String> schema = rest.exchange("/api/v1/resource-types/model/schema", HttpMethod.GET,
                new HttpEntity<>(ipHeaders()), String.class);
        assertEquals(200, schema.getStatusCode().value());
    }

    // ---------- 工具 ----------

    /** 上传一个小文本文件（git source）并等待 active，返回上传后的分支 head sha。 */
    private String uploadTextFile(String token, String repoId, String path, String text) throws Exception {
        byte[] bytes = text.getBytes(StandardCharsets.UTF_8);
        JsonNode up = initiateUpload(token, repoId, "main", headSha(token, repoId), path,
                bytes.length, sha256Hex(bytes), "text/plain");
        String uploadId = up.path("id").asText();
        awaitUploadStatus(token, uploadId, "uploading");
        putPart(partUrls(token, uploadId, List.of(1)).get(0).path("url").asText(), bytes);
        completeUpload(token, uploadId);
        awaitUploadStatus(token, uploadId, "completed");
        awaitFileStatus(token, repoId, path, "active");
        return headSha(token, repoId);
    }

    private static Set<String> pathsOf(ResponseEntity<String> resp) {
        Set<String> paths = new TreeSet<>();
        dataNode(resp).path("items").forEach(i -> paths.add(i.path("path").asText()));
        return paths;
    }

    static String sha256Hex(byte[] data) throws Exception {
        return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(data));
    }
}
