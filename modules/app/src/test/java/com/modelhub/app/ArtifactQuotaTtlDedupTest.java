package com.modelhub.app;

import com.fasterxml.jackson.databind.JsonNode;
import com.modelhub.artifact.domain.ObjectBlobEntity;
import com.modelhub.artifact.repo.ObjectBlobRepository;
import com.modelhub.identity.domain.NamespaceEntity;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 配额（05 §11）/ 下载 TTL 可见性分支（07 §2 SEC-02）/ 去重键对齐（03 §5.4）集成测试。
 * 配额经 @DynamicPropertySource 压小：单文件 8192B、仓库总量 12000B、并发上传 2。
 */
class ArtifactQuotaTtlDedupTest extends ArtifactTestSupport {

    @Autowired
    private ObjectBlobRepository blobs;

    @DynamicPropertySource
    static void quotaProps(DynamicPropertyRegistry registry) {
        registry.add("modelhub.artifact.quota.max-file-size-bytes", () -> 8192);
        registry.add("modelhub.artifact.quota.max-repo-total-bytes", () -> 12000);
        registry.add("modelhub.artifact.quota.max-concurrent-uploads", () -> 2);
    }

    /** TTL 分支（07 §2 SEC-02）：public → downloadUrlTtlSeconds(600)；private → private(300) 且 < 600。 */
    @Test
    void downloadSession_ttlBranchesByVisibility() throws Exception {
        Session owner = newUser("artttl");
        byte[] content = "ttl probe content".getBytes(StandardCharsets.UTF_8);

        // public 仓库：会话有效期 ≈ now+600s
        String pubRepo = createActiveRepo(owner);
        String pubPath = fullUpload(owner, pubRepo, "pub.txt", content, "text/plain");
        String pubFileId = awaitFileStatus(owner.accessToken(), pubRepo, pubPath, "active")
                .path("id").asText();
        OffsetDateTime pubBefore = OffsetDateTime.now();
        JsonNode pubDs = createDownloadSession(owner.accessToken(), pubRepo, pubFileId, null);
        long pubTtl = Duration.between(pubBefore,
                OffsetDateTime.parse(pubDs.path("expiresAt").asText())).getSeconds();
        assertTrue(pubTtl >= 595 && pubTtl <= 605, "public TTL 应≈600s，实际=" + pubTtl);

        // private 仓库：会话有效期 ≈ now+300s（SEC-02 必须 < 600s）
        ResponseEntity<String> create = createRepo(owner, userNamespaceId(owner), unique("privrepo"),
                "private", null, Map.of("license", "MIT"));
        String privRepo = dataNode(create).path("id").asText();
        awaitLifecycleHttp(owner.accessToken(), privRepo, "active");
        String privPath = fullUpload(owner, privRepo, "priv.txt", content, "text/plain");
        String privFileId = awaitFileStatus(owner.accessToken(), privRepo, privPath, "active")
                .path("id").asText();
        OffsetDateTime privBefore = OffsetDateTime.now();
        JsonNode privDs = createDownloadSession(owner.accessToken(), privRepo, privFileId, null);
        long privTtl = Duration.between(privBefore,
                OffsetDateTime.parse(privDs.path("expiresAt").asText())).getSeconds();
        assertTrue(privTtl >= 295 && privTtl <= 305, "private TTL 应≈300s，实际=" + privTtl);
        assertTrue(privTtl < 600, "SEC-02：private/gated 下载 URL 最长 10 分钟");
    }

    /** 配额：sizeBytes > quota.maxFileSizeBytes → 413（契约 initiateUpload PayloadTooLarge）。 */
    @Test
    void initiate_exceedsQuotaMaxFileSize_413() throws Exception {
        Session owner = newUser("artqsize");
        String repoId = createActiveRepo(owner);
        ResponseEntity<String> resp = initiateRaw(owner.accessToken(), repoId, "big.bin", 10_000,
                "application/octet-stream");
        assertEquals(HttpStatus.PAYLOAD_TOO_LARGE, resp.getStatusCode());
        assertEquals("VALIDATION_FAILED", errorCode(resp));
        assertTrue(resp.getBody().contains("quota.maxFileSizeBytes"), resp.getBody());
    }

    /** 配额：非终态上传会话达到 quota.maxConcurrentUploads → 429 RATE_LIMITED。 */
    @Test
    void initiate_concurrentUploadLimit_429() throws Exception {
        Session owner = newUser("artqconc");
        String repoId = createActiveRepo(owner);
        // 两个非终态会话（initiated→uploading 均计入），不 complete
        initiateUpload(owner.accessToken(), repoId, "main", headSha(owner.accessToken(), repoId),
                "c1.bin", 100, null, "application/octet-stream");
        initiateUpload(owner.accessToken(), repoId, "main", headSha(owner.accessToken(), repoId),
                "c2.bin", 100, null, "application/octet-stream");
        ResponseEntity<String> third = initiateRaw(owner.accessToken(), repoId, "c3.bin", 100,
                "application/octet-stream");
        assertEquals(HttpStatus.TOO_MANY_REQUESTS, third.getStatusCode());
        assertEquals("RATE_LIMITED", errorCode(third));
        assertTrue(third.getBody().contains("quota.maxConcurrentUploads"), third.getBody());
    }

    /** 配额：已发布（staging/active）总量 + 本次 > quota.maxRepoTotalBytes → 413；未超仍可发起。 */
    @Test
    void initiate_exceedsRepoTotalQuota_413() throws Exception {
        Session owner = newUser("artqtotal");
        String repoId = createActiveRepo(owner);
        byte[] content = new byte[8192];
        java.util.Arrays.fill(content, (byte) 'a');
        String firstPath = fullUpload(owner, repoId, "first.txt", content, "text/plain");
        awaitFileStatus(owner.accessToken(), repoId, firstPath, "active");

        // 8192 + 4000 > 12000 → 413
        ResponseEntity<String> over = initiateRaw(owner.accessToken(), repoId, "second.txt", 4000,
                "text/plain");
        assertEquals(HttpStatus.PAYLOAD_TOO_LARGE, over.getStatusCode());
        assertTrue(over.getBody().contains("quota.maxRepoTotalBytes"), over.getBody());
        // 8192 + 3000 <= 12000 → 201 正常发起
        ResponseEntity<String> fit = initiateRaw(owner.accessToken(), repoId, "third.txt", 3000,
                "text/plain");
        assertEquals(HttpStatus.CREATED, fit.getStatusCode());
    }

    /** 去重键对齐（03 §5.4）：同 (namespace, sha256, size) 复用同一 blob（单行 + 两个 file_version
     *  指向同一 object_blob_id，refCount >= 2；ref_count 为回收候选优化，Outbox 至少一次投递
     *  可能重复自增，不作精确断言）。同 sha 不同 size 无法自然构造（verifiedSha 由内容计算），
     *  由 V15 唯一键约束兜底。 */
    @Test
    void dedup_sameShaAndSize_singleBlobRow() throws Exception {
        Session owner = newUser("artdedup");
        String repo1 = createActiveRepo(owner);
        String repo2 = createActiveRepo(owner);
        byte[] content = ArtifactFlowTest.randomBytes(500);
        String sha = ArtifactFlowTest.sha256Hex(content);

        String path1 = fullUpload(owner, repo1, "weights/a.bin", content, "application/octet-stream");
        String path2 = fullUpload(owner, repo2, "weights/b.bin", content, "application/octet-stream");
        JsonNode item1 = awaitFileStatus(owner.accessToken(), repo1, path1, "active");
        JsonNode item2 = awaitFileStatus(owner.accessToken(), repo2, path2, "active");
        assertEquals("object", item1.path("contentSource").asText());
        assertEquals("object", item2.path("contentSource").asText());

        // 同 namespace 同 sha 同 size：finder 命中唯一一行（多行会抛 NonUnique）
        NamespaceEntity ns = namespaces.findBySlugIgnoreCase(owner.username()).orElseThrow();
        ObjectBlobEntity blob = blobs
                .findByNamespaceIdAndSha256AndSizeBytes(ns.getId(), sha, content.length)
                .orElseThrow();
        assertTrue(blob.getRefCount() >= 2, "去重应累计引用，实际 refCount=" + blob.getRefCount());
        assertEquals("available", blob.getStatus());
        assertEquals("clean", blob.getScanStatus());
        // 两个 file_version 指向同一 blob 行（真正的去重不变量）
        Long blob1 = fileVersions.findByPublicId(UUID.fromString(item1.path("id").asText()))
                .orElseThrow().getObjectBlobId();
        Long blob2 = fileVersions.findByPublicId(UUID.fromString(item2.path("id").asText()))
                .orElseThrow().getObjectBlobId();
        assertEquals(blob1, blob2, "同 sha+size 的两个文件版本应共享同一 blob");
        assertEquals(blob.getId(), blob1);
    }

    // ---------- 助手 ----------

    @Autowired
    private com.modelhub.artifact.repo.FileVersionRepository fileVersions;

    /** 发起上传（原始响应，供 4xx 断言）；size/类型按参数构造。 */
    private ResponseEntity<String> initiateRaw(String token, String repoId, String path, long sizeBytes,
                                               String contentType) {
        Map<String, Object> body = new HashMap<>();
        body.put("branch", "main");
        body.put("baseCommitSha", headSha(token, repoId));
        body.put("path", path);
        body.put("sizeBytes", sizeBytes);
        if (contentType != null) {
            body.put("contentType", contentType);
        }
        HttpHeaders h = bearer(token);
        h.setContentType(MediaType.APPLICATION_JSON);
        h.add("Idempotency-Key", UUID.randomUUID().toString());
        h.add("X-Test-Client-Ip", randomIp());
        return rest.exchange("/api/v1/repositories/" + repoId + "/uploads",
                HttpMethod.POST, new HttpEntity<>(body, h), String.class);
    }

    /** 全链路上传单分片文件直至 completed；provisioning 重试竞态导致 branch conflict 时
     *  换路径重试（Gitea auto-init 与 Outbox 至少一次投递的已知时序抖动），返回实际路径。 */
    private String fullUpload(Session owner, String repoId, String path, byte[] content,
                              String contentType) throws Exception {
        IllegalStateException last = null;
        for (int attempt = 1; attempt <= 3; attempt++) {
            String p = attempt == 1 ? path : path + ".r" + attempt;
            try {
                JsonNode up = initiateUpload(owner.accessToken(), repoId, "main",
                        headSha(owner.accessToken(), repoId), p, content.length,
                        ArtifactFlowTest.sha256Hex(content), contentType);
                String uploadId = up.path("id").asText();
                awaitUploadStatus(owner.accessToken(), uploadId, "uploading");
                putPart(partUrls(owner.accessToken(), uploadId, java.util.List.of(1)).get(0)
                        .path("url").asText(), content);
                completeUpload(owner.accessToken(), uploadId);
                awaitUploadStatus(owner.accessToken(), uploadId, "completed");
                return p;
            } catch (IllegalStateException | AssertionError e) {
                // conflict 在 awaitUploadStatus 中表现为超时 AssertionError（携带最后状态）
                last = new IllegalStateException(e.getMessage());
                if (attempt == 3 || !String.valueOf(e.getMessage()).contains("conflict")) {
                    throw e;
                }
            }
        }
        throw last;
    }
}
