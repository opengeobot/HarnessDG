package com.modelhub.app;

import com.fasterxml.jackson.databind.JsonNode;
import com.modelhub.artifact.storage.ObjectStorageService;
import com.modelhub.artifact.worker.UploadSessionJanitor;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpMethod;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 上传会话 Janitor（05 §5/§6.1）：会话过期后由定时任务主动终结——
 * 状态置 expired + Provider Multipart Abort；重复执行幂等。
 */
class UploadSessionJanitorTest extends ArtifactTestSupport {

    @Autowired
    private UploadSessionJanitor janitor;

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private ObjectStorageService storage;

    @Test
    void expiresOverdueSessionTerminatesMultipartAndIsIdempotent() throws Exception {
        Session owner = newUser("janitor");
        String repoId = createActiveRepo(owner);

        // 建立真实会话：Worker 异步初始化 MinIO Multipart 并回填 provider_upload_id
        JsonNode up = initiateUpload(owner.accessToken(), repoId, "main",
                headSha(owner.accessToken(), repoId), "docs/notes.txt", 64, null, "text/plain");
        String uploadId = up.path("id").asText();
        awaitUploadStatus(owner.accessToken(), uploadId, "uploading");

        UUID uploadPublicId = UUID.fromString(uploadId);
        Map<String, Object> row = jdbc.queryForMap(
                "SELECT status, provider_upload_id, object_key FROM upload_sessions WHERE public_id = ?",
                uploadPublicId);
        assertEquals("uploading", row.get("status"));
        String providerUploadId = (String) row.get("provider_upload_id");
        String objectKey = (String) row.get("object_key");
        assertNotNull(providerUploadId, "uploading 会话必须已回填 provider_upload_id");

        // 会话 TTL 默认 72h：直接把 expires_at 拨到过去触发 Janitor（05 §5 过期收敛）
        jdbc.update("UPDATE upload_sessions SET expires_at = now() - interval '1 second' "
                + "WHERE public_id = ?", uploadPublicId);

        int expired = janitor.expireOverdueSessions();
        assertTrue(expired >= 1, "至少应终结本用例会话，实际=" + expired);

        // DB 与 GET /uploads/{id} 均投影 expired（终态语义）
        assertEquals("expired", jdbc.queryForObject(
                "SELECT status FROM upload_sessions WHERE public_id = ?", String.class, uploadPublicId));
        JsonNode view = dataNode(rest.exchange("/api/v1/uploads/" + uploadId, HttpMethod.GET,
                new HttpEntity<>(bearer(owner.accessToken())), String.class));
        assertEquals("expired", view.path("status").asText());

        // Provider Multipart 已终止：ListParts 对已 abort 的 upload 报 NoSuchUpload
        assertTrue(listPartsFails(objectKey, providerUploadId),
                "abort 后 ListParts 应失败（upload 已不存在）");

        // 幂等：重跑不再处理（本会话已终态），状态保持 expired
        jdbc.update("UPDATE upload_sessions SET expires_at = now() - interval '1 second' "
                + "WHERE public_id = ?", uploadPublicId);
        janitor.expireOverdueSessions();
        assertEquals("expired", jdbc.queryForObject(
                "SELECT status FROM upload_sessions WHERE public_id = ?", String.class, uploadPublicId));
    }

    private boolean listPartsFails(String objectKey, String providerUploadId) {
        try {
            storage.listParts(objectKey, providerUploadId);
            return false;
        } catch (Exception e) {
            return true;
        }
    }
}
