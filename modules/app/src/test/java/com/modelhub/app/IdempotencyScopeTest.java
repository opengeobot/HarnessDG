package com.modelhub.app;

import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HashMap;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * 幂等域对齐（04 §10：principal + method + normalized path）集成证据：
 * 不同主体复用同一 Idempotency-Key 不互相污染；download-sessions 重放
 * 绑定文件校验（不同文件 409）；Idempotency-Key 缺失 400（契约 required）。
 */
class IdempotencyScopeTest extends ArtifactTestSupport {

    /** 不同用户 POST /repositories 复用同一 Key：各自独立受理（旧行为为 409 串扰）。 */
    @Test
    void createRepo_sameKeyDifferentUsers_bothSucceed() {
        Session alice = newUser("idscopeA");
        Session bob = newUser("idscopeB");
        String sharedKey = "scope-key-" + UUID.randomUUID();
        String aliceRepoName = unique("scope-a");

        ResponseEntity<String> first = createRepoWithKey(alice, aliceRepoName, sharedKey);
        assertEquals(HttpStatus.CREATED, first.getStatusCode(),
                "首个用户应 201: " + first.getBody());
        String firstRepoId = dataNode(first).path("id").asText();

        ResponseEntity<String> second = createRepoWithKey(bob, unique("scope-b"), sharedKey);
        assertEquals(HttpStatus.CREATED, second.getStatusCode(),
                "同 Key 不同主体不应被首个用户的幂等记录拦截: " + second.getBody());
        assertNotNull(dataNode(second).path("id").asText());

        // 同主体同 Key 同 body 重放：返回首次结果（相同 repo id），不触达业务创建
        ResponseEntity<String> replay = createRepoWithKey(alice, aliceRepoName, sharedKey);
        assertEquals(HttpStatus.CREATED, replay.getStatusCode());
        assertEquals(firstRepoId, dataNode(replay).path("id").asText());
    }

    /** 同仓库同 Key 绑定不同文件：第二个文件 → 409；同文件重放回放既有会话与其 fileId。 */
    @Test
    void downloadSession_sameKeyDifferentFile_409() throws Exception {
        Session owner = newUser("idscopeDs");
        String repoId = createActiveRepo(owner);
        String file1 = uploadTextFile(owner.accessToken(), repoId, "one.txt", "file one".getBytes(StandardCharsets.UTF_8));
        String file2 = uploadTextFile(owner.accessToken(), repoId, "two.txt", "file two".getBytes(StandardCharsets.UTF_8));
        String key = "ds-key-" + UUID.randomUUID();

        JsonNode ds = createDownloadSession(owner.accessToken(), repoId, file1, key);
        String sessionId = ds.path("sessionId").asText();

        // 同 Key 请求另一文件：幂等键已绑定 file1 → 409 CONFLICT
        ResponseEntity<String> conflict = downloadSessionWithKey(owner.accessToken(), repoId, file2, key);
        assertEquals(HttpStatus.CONFLICT, conflict.getStatusCode());
        assertEquals("CONFLICT", errorCode(conflict));

        // 同 Key 同文件重放：回放既有会话，fileId 必须是既有会话的文件（file1）
        JsonNode replay = createDownloadSession(owner.accessToken(), repoId, file1, key);
        assertEquals(sessionId, replay.path("sessionId").asText());
        assertEquals(file1, replay.path("fileId").asText());
    }

    /** 契约（openapi-v1）声明 Idempotency-Key required：缺失 → 400 VALIDATION_FAILED。 */
    @Test
    void downloadSession_missingIdempotencyKey_400() throws Exception {
        Session owner = newUser("idscopeMiss");
        String repoId = createActiveRepo(owner);
        String fileId = uploadTextFile(owner.accessToken(), repoId, "m.txt",
                "missing key".getBytes(StandardCharsets.UTF_8));

        HttpHeaders h = bearer(owner.accessToken());
        ResponseEntity<String> resp = rest.exchange(
                "/api/v1/repositories/" + repoId + "/files/" + fileId + "/download-sessions",
                HttpMethod.POST, new HttpEntity<>(h), String.class);
        assertEquals(HttpStatus.BAD_REQUEST, resp.getStatusCode());
        assertEquals("VALIDATION_FAILED", errorCode(resp));
    }

    // ---------- 助手 ----------

    /** 显式 Idempotency-Key 的建仓请求（body 由 name 参数决定，重放需复用首次 name）。 */
    private ResponseEntity<String> createRepoWithKey(Session owner, String name, String key) {
        Map<String, Object> body = new HashMap<>();
        body.put("namespaceId", userNamespaceId(owner));
        body.put("type", "model");
        body.put("metadataSchemaVersion", 1);
        body.put("name", name);
        body.put("visibility", "public");
        body.put("metadata", Map.of("license", "MIT"));
        HttpHeaders h = bearer(owner.accessToken());
        h.setContentType(MediaType.APPLICATION_JSON);
        h.add("Idempotency-Key", key);
        h.add("X-Test-Client-Ip", randomIp());
        return rest.exchange("/api/v1/repositories", HttpMethod.POST, new HttpEntity<>(body, h), String.class);
    }

    /** git source 小文件全链路上传，返回文件 publicId。 */
    private String uploadTextFile(String token, String repoId, String path, byte[] content) throws Exception {
        JsonNode up = initiateUpload(token, repoId, "main", headSha(token, repoId), path,
                content.length, sha256Hex(content), "text/plain");
        String uploadId = up.path("id").asText();
        awaitUploadStatus(token, uploadId, "uploading");
        putPart(partUrls(token, uploadId, List.of(1)).get(0).path("url").asText(), content);
        completeUpload(token, uploadId);
        awaitUploadStatus(token, uploadId, "completed");
        return awaitFileStatus(token, repoId, path, "active").path("id").asText();
    }

    private ResponseEntity<String> downloadSessionWithKey(String token, String repoId, String fileId, String key) {
        HttpHeaders h = bearer(token);
        h.add("Idempotency-Key", key);
        return rest.exchange("/api/v1/repositories/" + repoId + "/files/" + fileId + "/download-sessions",
                HttpMethod.POST, new HttpEntity<>(h), String.class);
    }

    static String sha256Hex(byte[] data) throws Exception {
        return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(data));
    }
}
