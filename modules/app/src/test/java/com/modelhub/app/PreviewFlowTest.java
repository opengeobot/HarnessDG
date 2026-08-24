package com.modelhub.app;

import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
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
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 预览链路集成测试（05 §5 / 04 Preview）：触发幂等（202 JobEnvelope）、
 * 轮询 200 ready（采样快照 + 版本键）、短周期下载 URL、stale 投影、
 * unsupported、授权（匿名 401 / 私有仓库 404 / 缺失 Idempotency-Key 400）。
 */
class PreviewFlowTest extends ArtifactTestSupport {

    /** 全链路：CSV 上传 → 触发 → 幂等复用 → ready 快照 → 下载 URL → 追加提交后 stale。 */
    @Test
    void previewGenerateReadyDownloadAndStale() throws Exception {
        Session owner = newUser("pvw1");
        String repoId = createActiveRepo(owner);
        uploadCsv(owner, repoId, "data.csv", "a,b,c\n1,2,3\n4,5,6\n");

        // 触发前置条件：无产物且无活跃任务 → 404（不自动触发）
        assertEquals(404, getPreview(owner.accessToken(), repoId, null).getStatusCode().value());

        // 缺失 Idempotency-Key → 400；匿名 → 401
        assertEquals(400, triggerPreview(owner.accessToken(), repoId, null, null, false).getStatusCode().value());
        assertEquals(401, triggerPreview(null, repoId, "key-12345678", null, false).getStatusCode().value());

        // 首次触发 → 202 JobEnvelope
        String key = UUID.randomUUID().toString();
        ResponseEntity<String> trigger = triggerPreview(owner.accessToken(), repoId, key, null, false);
        assertEquals(202, trigger.getStatusCode().value());
        JsonNode job = dataNode(trigger);
        assertEquals("preview.generate", job.path("type").asText());
        assertEquals("queued", job.path("status").asText());
        assertFalse(job.path("createdAt").asText().isBlank());
        String jobId = job.path("id").asText();

        // 活跃窗口内重复触发（同键/异键）→ 幂等复用同一 Job
        assertEquals(jobId, dataNode(triggerPreview(owner.accessToken(), repoId, key, null, false)).path("id").asText());
        assertEquals(jobId, dataNode(triggerPreview(owner.accessToken(), repoId,
                UUID.randomUUID().toString(), null, false)).path("id").asText());

        // 轮询 GET preview：期间 202 JobEnvelope，收敛后 200 PreviewEnvelope
        JsonNode preview = awaitPreviewStatus(owner.accessToken(), repoId, null, "ready");
        assertTrue(preview.path("sourceCommitSha").asText().matches("^[0-9a-fA-F]{40}$"),
                "sourceCommitSha 应为 40-hex commit: " + preview);
        assertEquals(headSha(owner.accessToken(), repoId), preview.path("sourceCommitSha").asText());
        assertTrue(preview.path("manifestHash").asText().matches("^[0-9a-fA-F]{64}$"));
        assertEquals(1, preview.path("policyVersion").asInt());
        assertEquals("head", preview.path("sampleStrategy").asText());
        assertEquals(3, preview.path("rowCount").asLong());
        assertEquals(3, preview.path("sampleCount").asLong());
        JsonNode sample = preview.path("sample");
        assertEquals(3, sample.size());
        assertEquals("a", sample.get(0).get(0).asText());
        assertEquals("b", sample.get(0).get(1).asText());
        assertEquals("1", sample.get(1).get(0).asText());

        // 下载：短周期预签名 URL（POST 与契约 GET 均可达）
        ResponseEntity<String> dl = downloadPreview(owner.accessToken(), repoId, null, HttpMethod.POST);
        assertEquals(200, dl.getStatusCode().value());
        String url = dataNode(dl).path("url").asText();
        assertTrue(url.startsWith("http"), "应返回绝对 URL: " + url);
        assertFalse(dataNode(dl).path("expiresAt").asText().isBlank());
        assertEquals(200, downloadPreview(owner.accessToken(), repoId, "main", HttpMethod.GET)
                .getStatusCode().value());
        // 预签名对象即采样快照（不可变 JSON）
        String snapshot = new String(getBytes(url), StandardCharsets.UTF_8);
        assertTrue(snapshot.contains("\"a\"") && snapshot.contains("\"4\""),
                "快照应包含采样行: " + snapshot);

        // 分支 head 前移（追加新提交）→ 既有 ready 预览投影为 stale（不自动重触发）
        uploadCsv(owner, repoId, "more.csv", "x,y\n7,8\n");
        awaitPreviewStatus(owner.accessToken(), repoId, null, "stale");

        // 私有仓库：非授权主体 GET → 404（防枚举）
        Session owner2 = newUser("pvw2");
        Session stranger = newUser("pvw3");
        ResponseEntity<String> priv = createRepo(owner2, userNamespaceId(owner2), unique("pvpriv"),
                "private", null, null);
        String privId = dataNode(priv).path("id").asText();
        awaitLifecycleHttp(owner2.accessToken(), privId, "active");
        assertEquals(404, getPreview(stranger.accessToken(), privId, null).getStatusCode().value());
        assertEquals(404, getPreview(null, privId, null).getStatusCode().value());
    }

    /** 无文本类文件（file_versions 为空）→ unsupported；下载返回 404。 */
    @Test
    void previewUnsupportedWhenNoTextFile() throws Exception {
        Session owner = newUser("pvw4");
        String repoId = createActiveRepo(owner);

        ResponseEntity<String> trigger = triggerPreview(owner.accessToken(), repoId,
                UUID.randomUUID().toString(), null, false);
        assertEquals(202, trigger.getStatusCode().value());
        JsonNode preview = awaitPreviewStatus(owner.accessToken(), repoId, null, "unsupported");
        // 契约 required 字段仍完整输出
        assertTrue(preview.path("sourceCommitSha").asText().matches("^[0-9a-fA-F]{40}$"));
        assertEquals(1, preview.path("policyVersion").asInt());
        assertNotNull(preview.path("manifestHash"));
        assertEquals(404, downloadPreview(owner.accessToken(), repoId, null, HttpMethod.GET)
                .getStatusCode().value());
    }

    // ---------- 助手 ----------

    private void uploadCsv(Session owner, String repoId, String path, String csv) throws Exception {
        byte[] content = csv.getBytes(StandardCharsets.UTF_8);
        // model 类型策略仅放行 text/plain；CSV 语义由 .csv 扩展名承载（采样按路径判定格式）
        JsonNode up = initiateUpload(owner.accessToken(), repoId, "main",
                headSha(owner.accessToken(), repoId), path, content.length,
                sha256Hex(content), "text/plain");
        String uploadId = up.path("id").asText();
        awaitUploadStatus(owner.accessToken(), uploadId, "uploading");
        putPart(partUrls(owner.accessToken(), uploadId, List.of(1)).get(0).path("url").asText(), content);
        completeUpload(owner.accessToken(), uploadId);
        awaitUploadStatus(owner.accessToken(), uploadId, "completed");
        awaitFileStatus(owner.accessToken(), repoId, path, "active");
    }

    private ResponseEntity<String> triggerPreview(String token, String repoId, String idempotencyKey,
                                                  String ref, boolean force) {
        HttpHeaders h = token == null ? ipHeaders() : bearer(token);
        h.setContentType(MediaType.APPLICATION_JSON);
        if (idempotencyKey != null) {
            h.add("Idempotency-Key", idempotencyKey);
        }
        Map<String, Object> body = new HashMap<>();
        if (ref != null) {
            body.put("ref", ref);
        }
        if (force) {
            body.put("force", true);
        }
        return rest.exchange("/api/v1/repositories/" + repoId + "/preview-jobs", HttpMethod.POST,
                new HttpEntity<>(body, h), String.class);
    }

    private ResponseEntity<String> getPreview(String token, String repoId, String ref) {
        HttpHeaders h = token == null ? ipHeaders() : bearer(token);
        String query = ref == null ? "" : "?ref=" + ref;
        return rest.exchange("/api/v1/repositories/" + repoId + "/preview" + query, HttpMethod.GET,
                new HttpEntity<>(h), String.class);
    }

    private ResponseEntity<String> downloadPreview(String token, String repoId, String ref, HttpMethod method) {
        HttpHeaders h = token == null ? ipHeaders() : bearer(token);
        String query = ref == null ? "" : "?ref=" + ref;
        return rest.exchange("/api/v1/repositories/" + repoId + "/preview/download" + query, method,
                new HttpEntity<>(h), String.class);
    }

    /** 轮询 GET preview 直至 200 且 status=expected（30s / 500ms，期间应为 202 JobEnvelope）。 */
    private JsonNode awaitPreviewStatus(String token, String repoId, String ref, String expected)
            throws Exception {
        long deadline = System.currentTimeMillis() + 30_000;
        ResponseEntity<String> last = null;
        while (System.currentTimeMillis() < deadline) {
            last = getPreview(token, repoId, ref);
            if (last.getStatusCode().value() == 200) {
                JsonNode data = dataNode(last);
                if (expected.equals(data.path("status").asText())) {
                    return data;
                }
            }
            Thread.sleep(500);
        }
        throw new AssertionError("等待预览 status=" + expected + " 超时，最后响应="
                + (last == null ? "?" : last.getStatusCode() + " " + last.getBody()));
    }

    static String sha256Hex(byte[] data) throws Exception {
        return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(data));
    }
}
