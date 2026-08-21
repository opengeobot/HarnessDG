package com.modelhub.app;

import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Arrays;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * M2b artifact 集成测试（TST-04 真实中间件证据）：上传/分片直传/校验/扫描/发布、
 * files 清单 + ETag、下载会话（git/object 双源）、If-Match 删除、gated 下载拦截。
 */
class ArtifactFlowTest extends ArtifactTestSupport {

    /** 小文本文件走 git source：上传→发布→清单→下载→删除 全链路。 */
    @Test
    void gitSourceUpload_fullLifecycle() throws Exception {
        Session owner = newUser("artgit");
        String repoId = createActiveRepo(owner);
        byte[] content = ("# Model " + unique("m") + "\n\nhello artifact world\n").getBytes(StandardCharsets.UTF_8);
        String sha = sha256Hex(content);

        JsonNode up = initiateUpload(owner.accessToken(), repoId, "main", headSha(owner.accessToken(), repoId),
                "README.md", content.length, sha, "text/plain");
        assertEquals("git", up.path("contentSource").asText());
        assertEquals("initiated", up.path("status").asText());
        String uploadId = up.path("id").asText();

        // worker 初始化 multipart 后签发 part URL 并直传
        awaitUploadStatus(owner.accessToken(), uploadId, "uploading");
        List<JsonNode> urls = partUrls(owner.accessToken(), uploadId, List.of(1));
        assertEquals(1, urls.size());
        assertEquals(1, urls.get(0).path("partNumber").asInt());
        assertNotNull(urls.get(0).path("url").asText());
        putPart(urls.get(0).path("url").asText(), content);

        // complete → 异步校验/扫描/发布（05 §6.3）
        JsonNode job = completeUpload(owner.accessToken(), uploadId);
        assertEquals("upload.verification", job.path("type").asText());
        JsonNode done = awaitUploadStatus(owner.accessToken(), uploadId, "completed");
        assertEquals(sha, done.path("verifiedSha256").asText());
        assertEquals("completed", done.path("status").asText());

        // files 清单（PG 真相源 + resolvedCommitSha）
        JsonNode item = awaitFileStatus(owner.accessToken(), repoId, "README.md", "active");
        assertEquals("git", item.path("contentSource").asText());
        assertEquals(content.length, item.path("sizeBytes").asLong());
        assertEquals("text/plain", item.path("contentType").asText());
        assertTrue(item.path("commitSha").asText().matches("^[0-9a-fA-F]{40}$"));

        // 下载会话：git source → 自建内容端点；幂等键复用同一会话（04 §10）
        JsonNode ds = createDownloadSession(owner.accessToken(), repoId, item.path("id").asText(), "ds-1");
        // sessionId 取自视图字段（url 以 /content 结尾，不能从路径截取）
        String sessionId = ds.path("sessionId").asText();
        JsonNode dsReplay = createDownloadSession(owner.accessToken(), repoId, item.path("id").asText(), "ds-1");
        assertEquals(ds.path("sessionId").asText(), dsReplay.path("sessionId").asText());

        ResponseEntity<String> contentResp = rest.exchange("/api/v1/downloads/" + sessionId + "/content",
                HttpMethod.GET, new HttpEntity<>(ipHeaders()), String.class);
        assertEquals(HttpStatus.OK, contentResp.getStatusCode());
        assertEquals(new String(content, StandardCharsets.UTF_8), contentResp.getBody());

        // If-Match 删除 → 异步 Git 删除提交 → 清单消失
        String etag = filesList(owner.accessToken(), repoId).getHeaders().getFirst("ETag");
        assertNotNull(etag);
        JsonNode del = deleteFile(owner.accessToken(), repoId, item.path("id").asText(), etag, null);
        assertEquals("file.delete", del.path("type").asText());
        awaitFileAbsent(owner.accessToken(), repoId, "README.md");
    }

    /** 大文件走 object source：多分片直传 + 预签名 GET 下载 + sha256 一致性。 */
    @Test
    void objectSourceUpload_multipartAndPresignedDownload() throws Exception {
        Session owner = newUser("artobj");
        String repoId = createActiveRepo(owner);
        byte[] content = randomBytes(20 * 1024 * 1024 + 12_345); // 2 分片（16MiB + 余量）
        String sha = sha256Hex(content);

        JsonNode up = initiateUpload(owner.accessToken(), repoId, "main", headSha(owner.accessToken(), repoId),
                "weights/model.bin", content.length, sha, "application/octet-stream");
        assertEquals("object", up.path("contentSource").asText());
        String uploadId = up.path("id").asText();
        awaitUploadStatus(owner.accessToken(), uploadId, "uploading");

        List<JsonNode> urls = partUrls(owner.accessToken(), uploadId, List.of(1, 2));
        assertEquals(2, urls.size());
        int partSize = up.path("partSize").asInt();
        assertEquals(content.length - partSize, urls.get(1).path("expectedSizeBytes").asLong());
        putPart(urls.get(0).path("url").asText(), Arrays.copyOfRange(content, 0, partSize));
        putPart(urls.get(1).path("url").asText(), Arrays.copyOfRange(content, partSize, content.length));

        completeUpload(owner.accessToken(), uploadId);
        JsonNode done = awaitUploadStatus(owner.accessToken(), uploadId, "completed");
        assertEquals(sha, done.path("verifiedSha256").asText());

        JsonNode item = awaitFileStatus(owner.accessToken(), repoId, "weights/model.bin", "active");
        assertEquals("object", item.path("contentSource").asText());
        // object source 下载走 MinIO 预签名 GET（05 §11 隔离检查：clean 才可下载）
        JsonNode ds = createDownloadSession(owner.accessToken(), repoId, item.path("id").asText(), null);
        byte[] downloaded = getBytes(ds.path("url").asText());
        assertArrayEquals(content, downloaded);
        // 下载会话 URL 为预签名对象 URL（非自建端点）
        assertTrue(ds.path("url").asText().contains("/artifacts/objects/"));
    }

    /** baseCommitSha 过期（与分支 head 不一致）→ 422 stale（05 §6.1 第 5 条）。 */
    @Test
    void initiate_withStaleBaseCommitSha_422() throws Exception {
        Session owner = newUser("artstale");
        String repoId = createActiveRepo(owner);
        Map<String, Object> body = Map.of(
                "branch", "main",
                "baseCommitSha", "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa",
                "path", "x.txt",
                "sizeBytes", 10);
        HttpHeaders h = bearer(owner.accessToken());
        h.setContentType(org.springframework.http.MediaType.APPLICATION_JSON);
        h.add("Idempotency-Key", java.util.UUID.randomUUID().toString());
        h.add("X-Test-Client-Ip", randomIp());
        ResponseEntity<String> resp = rest.exchange("/api/v1/repositories/" + repoId + "/uploads",
                HttpMethod.POST, new HttpEntity<>(body, h), String.class);
        assertEquals(HttpStatus.UNPROCESSABLE_ENTITY, resp.getStatusCode());
        assertEquals("VALIDATION_FAILED", errorCode(resp));
        assertTrue(resp.getBody().contains("\"stale\""));
    }

    /** 文件删除前置条件：If-Match 与当前 head 不一致 → 412（04 deleteFile）。 */
    @Test
    void deleteFile_withStaleIfMatch_412() throws Exception {
        Session owner = newUser("artdel");
        String repoId = createActiveRepo(owner);
        byte[] content = "to be deleted".getBytes(StandardCharsets.UTF_8);
        JsonNode up = initiateUpload(owner.accessToken(), repoId, "main",
                headSha(owner.accessToken(), repoId), "tmp.txt", content.length,
                sha256Hex(content), "text/plain");
        String uploadId = up.path("id").asText();
        awaitUploadStatus(owner.accessToken(), uploadId, "uploading");
        putPart(partUrls(owner.accessToken(), uploadId, List.of(1)).get(0).path("url").asText(), content);
        completeUpload(owner.accessToken(), uploadId);
        awaitUploadStatus(owner.accessToken(), uploadId, "completed");
        JsonNode item = awaitFileStatus(owner.accessToken(), repoId, "tmp.txt", "active");

        ResponseEntity<String> stale = deleteFileRaw(owner.accessToken(), repoId, item.path("id").asText(),
                "\"bbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb\"", null);
        assertEquals(HttpStatus.PRECONDITION_FAILED, stale.getStatusCode());
        assertEquals("PRECONDITION_FAILED", errorCode(stale));
        // 正确 ETag 可删除
        String etag = filesList(owner.accessToken(), repoId).getHeaders().getFirst("ETag");
        deleteFile(owner.accessToken(), repoId, item.path("id").asText(), etag, null);
        awaitFileAbsent(owner.accessToken(), repoId, "tmp.txt");
    }

    /** gated 仓库：授权成功但无有效 grant 时下载会话签发被拒（02 §5 第 7 步 / 05 §11）。 */
    @Test
    void gatedRepo_downloadSession_forbiddenWithoutGrant() throws Exception {
        Session owner = newUser("artgate");
        ResponseEntity<String> create = createRepo(owner, userNamespaceId(owner), unique("gatedrepo"),
                "public", Boolean.TRUE, Map.of("license", "MIT"));
        String repoId = dataNode(create).path("id").asText();
        awaitLifecycleHttp(owner.accessToken(), repoId, "active");
        byte[] content = "gated content".getBytes(StandardCharsets.UTF_8);
        JsonNode up = initiateUpload(owner.accessToken(), repoId, "main",
                headSha(owner.accessToken(), repoId), "g.txt", content.length,
                sha256Hex(content), "text/plain");
        String uploadId = up.path("id").asText();
        awaitUploadStatus(owner.accessToken(), uploadId, "uploading");
        putPart(partUrls(owner.accessToken(), uploadId, List.of(1)).get(0).path("url").asText(), content);
        completeUpload(owner.accessToken(), uploadId);
        awaitUploadStatus(owner.accessToken(), uploadId, "completed");
        String fileId = awaitFileStatus(owner.accessToken(), repoId, "g.txt", "active").path("id").asText();

        Session stranger = newUser("artstranger");
        ResponseEntity<String> denied = downloadSessionRaw(stranger.accessToken(), repoId, fileId);
        assertEquals(HttpStatus.FORBIDDEN, denied.getStatusCode());
        assertEquals("FORBIDDEN", errorCode(denied));
        // 匿名同样被拦
        ResponseEntity<String> anon = downloadSessionRaw(null, repoId, fileId);
        assertEquals(HttpStatus.FORBIDDEN, anon.getStatusCode());
        // gated 仓库的文件清单（目录浏览）同样受 gated 策略保护：匿名/无 grant → 403（02 §4）
        ResponseEntity<String> files = filesList(null, repoId);
        assertEquals(HttpStatus.FORBIDDEN, files.getStatusCode());
        // owner 本人有 grant，可正常浏览
        ResponseEntity<String> filesOwner = filesList(owner.accessToken(), repoId);
        assertEquals(HttpStatus.OK, filesOwner.getStatusCode());
    }

    /** branches/commits 直读 Gitea 投影；匿名 GET 可达（目录只读白名单）。 */
    @Test
    void browse_branchesAndCommits_anonymousReadable() throws Exception {
        Session owner = newUser("artbrowse");
        String repoId = createActiveRepo(owner);

        ResponseEntity<String> branchesResp = rest.exchange(
                "/api/v1/repositories/" + repoId + "/branches", HttpMethod.GET,
                new HttpEntity<>(ipHeaders()), String.class);
        assertEquals(HttpStatus.OK, branchesResp.getStatusCode());
        JsonNode branches = dataNode(branchesResp).path("items");
        boolean main = false;
        for (JsonNode b : branches) {
            if ("main".equals(b.path("name").asText())) {
                main = true;
                assertTrue(b.path("isDefault").asBoolean());
                assertTrue(b.path("headCommitSha").asText().matches("^[0-9a-fA-F]{40}$"));
            }
        }
        assertTrue(main, "branches 应包含默认分支 main");

        // 上传一个文件后 commits 投影非空且最新 sha 与 head 一致
        byte[] content = "browse me".getBytes(StandardCharsets.UTF_8);
        JsonNode up = initiateUpload(owner.accessToken(), repoId, "main",
                headSha(owner.accessToken(), repoId), "b.txt", content.length,
                sha256Hex(content), "text/plain");
        String uploadId = up.path("id").asText();
        awaitUploadStatus(owner.accessToken(), uploadId, "uploading");
        putPart(partUrls(owner.accessToken(), uploadId, List.of(1)).get(0).path("url").asText(), content);
        completeUpload(owner.accessToken(), uploadId);
        awaitUploadStatus(owner.accessToken(), uploadId, "completed");

        ResponseEntity<String> commitsResp = rest.exchange(
                "/api/v1/repositories/" + repoId + "/commits", HttpMethod.GET,
                new HttpEntity<>(ipHeaders()), String.class);
        assertEquals(HttpStatus.OK, commitsResp.getStatusCode());
        JsonNode commits = dataNode(commitsResp).path("items");
        assertTrue(commits.size() >= 2, "应有 auto_init 与上传两个提交");
        assertEquals(headSha(owner.accessToken(), repoId), commits.get(0).path("sha").asText());
        assertFalse(commits.get(0).path("message").asText().isBlank());
    }

    // ---------- 工具 ----------

    static String sha256Hex(byte[] data) throws Exception {
        return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(data));
    }

    static byte[] randomBytes(int n) {
        byte[] b = new byte[n];
        new Random(42).nextBytes(b);
        return b;
    }
}
