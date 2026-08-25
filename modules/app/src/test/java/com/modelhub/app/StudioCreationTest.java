package com.modelhub.app;

import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 创空间（studio）创建回归：studio_profiles.publish_status NOT NULL，
 * 新建投影必须赋默认 draft（历史缺陷：projectStudio 未设默认值 → 500 DEPENDENCY_UNAVAILABLE）。
 */
class StudioCreationTest extends CatalogTestSupport {

    @Test
    void studio_repo_creation_reaches_active() throws Exception {
        Session owner = newUser("studio");
        String nsId = userNamespaceId(owner);

        Map<String, Object> metadata = new HashMap<>();
        metadata.put("scenes", List.of("workplace"));
        metadata.put("tags", List.of("chat"));
        metadata.put("runtimeType", "gradio");

        Map<String, Object> body = new HashMap<>();
        body.put("namespaceId", nsId);
        body.put("type", "studio");
        body.put("metadataSchemaVersion", 1);
        body.put("name", unique("studio-ws"));
        body.put("displayName", "演示工作空间");
        body.put("visibility", "public");
        body.put("metadata", metadata);

        HttpHeaders h = bearer(owner.accessToken());
        h.setContentType(MediaType.APPLICATION_JSON);
        h.add("Idempotency-Key", UUID.randomUUID().toString());
        h.add("X-Test-Client-Ip", randomIp());
        ResponseEntity<String> resp = rest.exchange("/api/v1/repositories", HttpMethod.POST,
                new HttpEntity<>(body, h), String.class);
        assertTrue(resp.getStatusCode().is2xxSuccessful(),
                "studio 创建应受理，实际=" + resp.getStatusCode() + " body=" + resp.getBody());

        JsonNode data = dataNode(resp);
        assertEquals("studio", data.path("type").asText());
        // provisioning → active（saga 异步完成 gitea/minio 资源；缺陷路径此处为 500 或卡在 provisioning）
        JsonNode detail = awaitLifecycleHttp(owner.accessToken(), data.path("id").asText(), "active");
        assertEquals("studio", detail.path("type").asText());
    }
}
