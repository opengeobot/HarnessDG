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
 * 标签字典化回归（仓库维护与标签字典化计划 §A/§B）：V20 起 model/dataset/studio
 * schema v2 的 tags 绑定 'tag' 字典 —— 未注册值 422 unknown_taxonomy_value；
 * v1 显式提交不受影响（向后兼容）；PATCH 升级 v2 后 metadataSchemaVersion 随之升版。
 */
class TagsDictValidationTest extends CatalogTestSupport {

    /** v2 创建（body 组装独立于 CatalogTestSupport.createRepo 的 v1 硬编码）。 */
    private ResponseEntity<String> createRepoV2(Session owner, String nsId, String name,
                                                Map<String, Object> metadata) {
        Map<String, Object> body = new HashMap<>();
        body.put("namespaceId", nsId);
        body.put("type", "model");
        body.put("metadataSchemaVersion", 2);
        body.put("name", name);
        body.put("visibility", "public");
        body.put("metadata", metadata);
        HttpHeaders h = bearer(owner.accessToken());
        h.setContentType(MediaType.APPLICATION_JSON);
        h.add("Idempotency-Key", UUID.randomUUID().toString());
        h.add("X-Test-Client-Ip", randomIp());
        return rest.exchange("/api/v1/repositories", HttpMethod.POST, new HttpEntity<>(body, h), String.class);
    }

    private static Map<String, Object> licensed(Map<String, Object> metadata) {
        Map<String, Object> m = new HashMap<>(metadata);
        m.put("license", "mit");
        return m;
    }

    @Test
    void v2_create_with_dict_tag_accepted() throws Exception {
        Session owner = newUser("tagok");
        String nsId = userNamespaceId(owner);
        ResponseEntity<String> resp = createRepoV2(owner, nsId, unique("tag-ok"),
                licensed(Map.of("tags", List.of("chat"))));
        assertTrue(resp.getStatusCode().is2xxSuccessful(),
                "字典内标签应受理，实际=" + resp.getStatusCode() + " body=" + resp.getBody());
        JsonNode detail = awaitLifecycleHttp(owner.accessToken(), dataNode(resp).path("id").asText(), "active");
        assertEquals(2, detail.path("metadataSchemaVersion").asInt());
    }

    @Test
    void v2_create_with_unknown_tag_rejected_422() throws Exception {
        Session owner = newUser("tagbad");
        String nsId = userNamespaceId(owner);
        ResponseEntity<String> resp = createRepoV2(owner, nsId, unique("tag-bad"),
                licensed(Map.of("tags", List.of("not-exist-tag"))));
        assertEquals(422, resp.getStatusCode().value(), "body=" + resp.getBody());
        assertEquals("METADATA_SCHEMA_INVALID", errorCode(resp));
        JsonNode details = JSON.readTree(resp.getBody()).path("details");
        boolean matched = false;
        for (JsonNode d : details) {
            if ("tags[0]".equals(d.path("field").asText())
                    && "unknown_taxonomy_value".equals(d.path("reason").asText())) {
                matched = true;
            }
        }
        assertTrue(matched, "应含 tags[0]=unknown_taxonomy_value 明细，实际=" + resp.getBody());
    }

    @Test
    void patch_upgrades_to_v2_and_validates_tags() throws Exception {
        Session owner = newUser("tagup");
        String nsId = userNamespaceId(owner);
        // v1 创建（显式版本，标签不校验 —— 向后兼容证据）
        ResponseEntity<String> created = createRepo(owner, nsId, unique("tag-up"), "public", null,
                licensed(Map.of("tags", List.of("any-free-text-tag"))));
        assertTrue(created.getStatusCode().is2xxSuccessful(),
                "v1 显式提交任意标签应通过（v1 未绑定字典），body=" + created.getBody());
        JsonNode detail = awaitLifecycleHttp(owner.accessToken(), dataNode(created).path("id").asText(), "active");
        long version = detail.path("version").asLong();

        // PATCH 携带 v2 + If-Match：合法标签 → 200 且 schema 版本升 2
        ResponseEntity<String> ok = patchRepo(owner.accessToken(), detail.path("id").asText(),
                Map.of("metadata", licensed(Map.of("tags", List.of("chat"))), "metadataSchemaVersion", 2),
                etagOfVersion(version));
        assertEquals(200, ok.getStatusCode().value(), "body=" + ok.getBody());
        assertEquals(2, dataNode(ok).path("metadataSchemaVersion").asInt());

        // PATCH v2 非法标签 → 422（版本已升，校验即时生效）
        ResponseEntity<String> bad = patchRepo(owner.accessToken(), detail.path("id").asText(),
                Map.of("metadata", licensed(Map.of("tags", List.of("ghost-tag"))), "metadataSchemaVersion", 2),
                etagOfVersion(version + 1));
        assertEquals(422, bad.getStatusCode().value(), "body=" + bad.getBody());
        assertEquals("METADATA_SCHEMA_INVALID", errorCode(bad));
    }
}
