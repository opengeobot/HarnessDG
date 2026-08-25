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
 * 数据集（dataset）创建回归：dataset_profiles.data_formats JSONB NOT NULL，
 * 投影必须写入 metadata.dataFormats（缺省空数组）
 * （历史缺陷：projectDataset 未写 data_formats → 500 DEPENDENCY_UNAVAILABLE）。
 */
class DatasetCreationTest extends CatalogTestSupport {

    @Test
    void dataset_repo_creation_reaches_active() throws Exception {
        Session owner = newUser("dataset");
        String nsId = userNamespaceId(owner);

        Map<String, Object> metadata = new HashMap<>();
        metadata.put("license", "cc-by-4.0");
        metadata.put("task", "text-classification");
        metadata.put("dataFormats", List.of("json", "csv"));
        metadata.put("estimatedRows", 1234);
        metadata.put("tags", List.of("chat"));

        Map<String, Object> body = new HashMap<>();
        body.put("namespaceId", nsId);
        body.put("type", "dataset");
        body.put("metadataSchemaVersion", 1);
        body.put("name", unique("ds-corpus"));
        body.put("displayName", "演示数据集");
        body.put("visibility", "public");
        body.put("metadata", metadata);

        HttpHeaders h = bearer(owner.accessToken());
        h.setContentType(MediaType.APPLICATION_JSON);
        h.add("Idempotency-Key", UUID.randomUUID().toString());
        h.add("X-Test-Client-Ip", randomIp());
        ResponseEntity<String> resp = rest.exchange("/api/v1/repositories", HttpMethod.POST,
                new HttpEntity<>(body, h), String.class);
        assertTrue(resp.getStatusCode().is2xxSuccessful(),
                "dataset 创建应受理，实际=" + resp.getStatusCode() + " body=" + resp.getBody());

        JsonNode data = dataNode(resp);
        assertEquals("dataset", data.path("type").asText());
        // provisioning → active（缺陷路径投影 data_formats NOT NULL 违反 → 500）
        JsonNode detail = awaitLifecycleHttp(owner.accessToken(), data.path("id").asText(), "active");
        assertEquals("dataset", detail.path("type").asText());
    }
}
