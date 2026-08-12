package com.modelhub.app;

import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.Assertions;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;

import java.net.URI;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Artifact 集成测试基座：MinIO 容器与凭据由 BaseIntegrationTest 统一注册
 * （S3Client Bean 无条件创建，所有上下文必需）；本类仅提供上传/分片/下载/
 * 删除链路的公共断言助手。
 */
public abstract class ArtifactTestSupport extends CatalogTestSupport {

    // ---------- 上传链路助手 ----------

    /** 建仓库并等待 provisioning 完成（active，Git 绑定就绪）。 */
    protected String createActiveRepo(Session owner) throws Exception {
        ResponseEntity<String> resp = createRepo(owner, userNamespaceId(owner), unique("artrepo"),
                "public", null, Map.of("license", "MIT"));
        if (resp.getStatusCode().value() != 201) {
            throw new IllegalStateException("create repo failed: " + resp.getStatusCode() + " " + resp.getBody());
        }
        String repoId = dataNode(resp).path("id").asText();
        awaitLifecycleHttp(owner.accessToken(), repoId, "active");
        return repoId;
    }

    /** 当前分支 head（files 清单 resolvedCommitSha，即 04 FilePageEnvelope）。 */
    protected String headSha(String token, String repoId) {
        ResponseEntity<String> resp = filesList(token, repoId);
        if (!resp.getStatusCode().is2xxSuccessful()) {
            throw new IllegalStateException("files list failed: " + resp.getStatusCode() + " " + resp.getBody());
        }
        return dataNode(resp).path("resolvedCommitSha").asText();
    }

    protected ResponseEntity<String> filesList(String token, String repoId) {
        HttpHeaders h = token == null ? ipHeaders() : bearer(token);
        return rest.exchange("/api/v1/repositories/" + repoId + "/files", HttpMethod.GET,
                new HttpEntity<>(h), String.class);
    }

    protected JsonNode initiateUpload(String token, String repoId, String branch, String baseSha,
                                      String path, long sizeBytes, String sha256, String contentType) {
        Map<String, Object> body = new HashMap<>();
        body.put("branch", branch);
        body.put("baseCommitSha", baseSha);
        body.put("path", path);
        body.put("sizeBytes", sizeBytes);
        if (sha256 != null) {
            body.put("sha256", sha256);
        }
        if (contentType != null) {
            body.put("contentType", contentType);
        }
        HttpHeaders h = bearer(token);
        h.setContentType(MediaType.APPLICATION_JSON);
        h.add("Idempotency-Key", UUID.randomUUID().toString());
        h.add("X-Test-Client-Ip", randomIp());
        ResponseEntity<String> resp = rest.exchange("/api/v1/repositories/" + repoId + "/uploads",
                HttpMethod.POST, new HttpEntity<>(body, h), String.class);
        if (resp.getStatusCode().value() != 201) {
            throw new IllegalStateException("initiate failed: " + resp.getStatusCode() + " " + resp.getBody());
        }
        return dataNode(resp);
    }

    /** 轮询上传会话直至目标状态；failed/aborted/expired 提前失败并携带响应体。 */
    protected JsonNode awaitUploadStatus(String token, String uploadId, String expected) throws Exception {
        long deadline = System.currentTimeMillis() + 120_000;
        String last = "";
        while (System.currentTimeMillis() < deadline) {
            ResponseEntity<String> resp = rest.exchange("/api/v1/uploads/" + uploadId, HttpMethod.GET,
                    new HttpEntity<>(bearer(token)), String.class);
            JsonNode data = dataNode(resp);
            last = data.path("status").asText();
            if (expected.equals(last)) {
                return data;
            }
            if (List.of("failed", "aborted", "expired").contains(last)) {
                throw new IllegalStateException("上传提前进入终态 " + last + ": " + resp.getBody());
            }
            Thread.sleep(300);
        }
        Assertions.fail("等待上传状态=" + expected + " 超时，最后状态=" + last);
        return null;
    }

    protected List<JsonNode> partUrls(String token, String uploadId, List<Integer> partNumbers) {
        ResponseEntity<String> resp = postJson(token, "/api/v1/uploads/" + uploadId + "/part-urls",
                Map.of("partNumbers", partNumbers), null);
        if (!resp.getStatusCode().is2xxSuccessful()) {
            throw new IllegalStateException("part-urls failed: " + resp.getStatusCode() + " " + resp.getBody());
        }
        List<JsonNode> out = new ArrayList<>();
        dataNode(resp).path("items").forEach(out::add);
        return out;
    }

    /** 客户端直传分片到预签名 URL（模拟真实浏览器/CLI 上传）。
     *  MinIO 对预签名 UploadPart 强制要求 x-amz-content-sha256 头且该头已签名
     *  （SignedHeaders 含 x-amz-content-sha256，服务端签发保证），客户端必须带同值头。 */
    protected void putPart(String presignedUrl, byte[] bytes) {
        HttpHeaders h = ipHeaders();
        h.add("x-amz-content-sha256", "UNSIGNED-PAYLOAD");
        // 必须传 URI：RestTemplate 对 String URL 经 DefaultUriBuilderFactory 二次编码
        // （%3B→%253B），MinIO 解码后 SignedHeaders 解析失败返回 MissingFields
        ResponseEntity<String> resp = rest.exchange(URI.create(presignedUrl), HttpMethod.PUT,
                new HttpEntity<>(bytes, h), String.class);
        if (!resp.getStatusCode().is2xxSuccessful()) {
            throw new IllegalStateException("part PUT failed: " + resp.getStatusCode() + " " + resp.getBody()
                    + " url=" + presignedUrl);
        }
    }

    protected JsonNode completeUpload(String token, String uploadId) {
        HttpHeaders h = bearer(token);
        h.add("Idempotency-Key", UUID.randomUUID().toString());
        h.add("X-Test-Client-Ip", randomIp());
        ResponseEntity<String> resp = rest.exchange("/api/v1/uploads/" + uploadId + ":complete",
                HttpMethod.POST, new HttpEntity<>(h), String.class);
        if (resp.getStatusCode().value() != 202) {
            throw new IllegalStateException("complete failed: " + resp.getStatusCode() + " " + resp.getBody());
        }
        return dataNode(resp);
    }

    /** 轮询文件清单直至目标路径达到期望状态。 */
    protected JsonNode awaitFileStatus(String token, String repoId, String path, String expected) throws Exception {
        long deadline = System.currentTimeMillis() + 60_000;
        while (System.currentTimeMillis() < deadline) {
            JsonNode items = dataNode(filesList(token, repoId));
            for (JsonNode it : items.path("items")) {
                if (path.equals(it.path("path").asText())) {
                    String status = it.path("status").asText();
                    if (expected.equals(status)) {
                        return it;
                    }
                }
            }
            Thread.sleep(300);
        }
        Assertions.fail("等待文件 " + path + " 状态=" + expected + " 超时");
        return null;
    }

    protected void awaitFileAbsent(String token, String repoId, String path) throws Exception {
        long deadline = System.currentTimeMillis() + 60_000;
        while (System.currentTimeMillis() < deadline) {
            JsonNode items = dataNode(filesList(token, repoId));
            boolean found = false;
            for (JsonNode it : items.path("items")) {
                if (path.equals(it.path("path").asText())) {
                    found = true;
                    break;
                }
            }
            if (!found) {
                return;
            }
            Thread.sleep(300);
        }
        Assertions.fail("等待文件 " + path + " 从清单消失超时");
    }

    // ---------- 下载/删除链路助手 ----------

    /** 签发下载会话；断言 201 后返回 data（DownloadSession 视图）。 */
    protected JsonNode createDownloadSession(String token, String repoId, String fileId, String idempotencyKey) {
        HttpHeaders h = token == null ? ipHeaders() : bearer(token);
        if (idempotencyKey != null) {
            h.add("Idempotency-Key", idempotencyKey);
        }
        ResponseEntity<String> resp = rest.exchange(
                "/api/v1/repositories/" + repoId + "/files/" + fileId + "/download-sessions",
                HttpMethod.POST, new HttpEntity<>(h), String.class);
        if (resp.getStatusCode().value() != 201) {
            throw new IllegalStateException("download-session failed: " + resp.getStatusCode() + " " + resp.getBody());
        }
        return dataNode(resp);
    }

    protected ResponseEntity<String> downloadSessionRaw(String token, String repoId, String fileId) {
        HttpHeaders h = token == null ? ipHeaders() : bearer(token);
        return rest.exchange("/api/v1/repositories/" + repoId + "/files/" + fileId + "/download-sessions",
                HttpMethod.POST, new HttpEntity<>(h), String.class);
    }

    protected JsonNode deleteFile(String token, String repoId, String fileId, String ifMatch, String idempotencyKey) {
        HttpHeaders h = bearer(token);
        h.add("If-Match", ifMatch);
        if (idempotencyKey != null) {
            h.add("Idempotency-Key", idempotencyKey);
        }
        h.add("X-Test-Client-Ip", randomIp());
        ResponseEntity<String> resp = rest.exchange("/api/v1/repositories/" + repoId + "/files/" + fileId,
                HttpMethod.DELETE, new HttpEntity<>(h), String.class);
        if (resp.getStatusCode().value() != 202) {
            throw new IllegalStateException("delete failed: " + resp.getStatusCode() + " " + resp.getBody());
        }
        return dataNode(resp);
    }

    protected ResponseEntity<String> deleteFileRaw(String token, String repoId, String fileId,
                                                   String ifMatch, String idempotencyKey) {
        HttpHeaders h = bearer(token);
        if (ifMatch != null) {
            h.add("If-Match", ifMatch);
        }
        if (idempotencyKey != null) {
            h.add("Idempotency-Key", idempotencyKey);
        }
        h.add("X-Test-Client-Ip", randomIp());
        return rest.exchange("/api/v1/repositories/" + repoId + "/files/" + fileId,
                HttpMethod.DELETE, new HttpEntity<>(h), String.class);
    }

    /** 经预签名 URL 下载对象（object source）。 */
    protected byte[] getBytes(String url) {
        // 同 putPart：预签名 URL 含 %XX 编码，传 URI 避免 RestTemplate 二次编码
        ResponseEntity<byte[]> resp = rest.exchange(URI.create(url), HttpMethod.GET,
                new HttpEntity<>(ipHeaders()), byte[].class);
        if (!resp.getStatusCode().is2xxSuccessful()) {
            throw new IllegalStateException("GET " + url + " failed: " + resp.getStatusCode());
        }
        return resp.getBody();
    }
}
