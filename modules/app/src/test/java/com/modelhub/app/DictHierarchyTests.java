package com.modelhub.app;

import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 字典统一 + 字典层级集成测试（字典统一计划 §三/§八）：
 * 两级层级守卫（跨字典父项/三级/有子项禁删/父项可改）、市场 options 返回迁移后的
 * 字典数据（9 组 + 双语 + 层级）、任务投影外键经 V18 迁移后仍指向正确字典项。
 */
class DictHierarchyTests extends CatalogTestSupport {

    @Autowired
    private JdbcTemplate jdbc;

    private static final Set<String> MARKET_DICT_CODES = Set.of(
            "framework", "license", "architecture", "language", "tag",
            "capability", "scene", "model_task", "dataset_task");

    // ---------- 1. 两级层级守卫 ----------

    @Test
    void dictItemHierarchyGuardsAndTwoLevelCap() {
        Session root = login("platform-root", "Boot-Strap-1x");
        String idem = UUID.randomUUID().toString();

        String dictA = unique("dh_dicta_").toLowerCase();
        String dictB = unique("dh_dictb_").toLowerCase();
        String dictIdA = createDict(root, dictA, idem);
        String dictIdB = createDict(root, dictB, idem);

        // 根项与子项
        ResponseEntity<String> grp = createItem(root, dictIdA,
                Map.of("itemValue", "grp", "labelZh", "分组", "labelEn", "Group"), idem);
        assertEquals(201, grp.getStatusCode().value(), "创建根项失败: " + grp.getBody());
        assertTrue(dataNode(grp).path("parentItemValue").isNull(), "根项 parentItemValue 应为 null");

        ResponseEntity<String> leaf = createItem(root, dictIdA,
                Map.of("itemValue", "leaf", "labelZh", "叶子", "labelEn", "Leaf",
                        "parentItemValue", "grp"), idem);
        assertEquals(201, leaf.getStatusCode().value(), "创建子项失败: " + leaf.getBody());
        assertEquals("grp", dataNode(leaf).path("parentItemValue").asText());
        long leafId = dataNode(leaf).path("id").asLong();

        // 三级封顶 400；跨字典父项 400；未知父项 400
        assertEquals(400, createItem(root, dictIdA,
                Map.of("itemValue", "deep", "labelZh", "三级", "labelEn", "Deep",
                        "parentItemValue", "leaf"), idem).getStatusCode().value());
        assertEquals(400, createItem(root, dictIdB,
                Map.of("itemValue", "foreign", "labelZh", "跨库", "labelEn", "Foreign",
                        "parentItemValue", "grp"), idem).getStatusCode().value());
        assertEquals(400, createItem(root, dictIdA,
                Map.of("itemValue", "orphan", "labelZh", "孤儿", "labelEn", "Orphan",
                        "parentItemValue", "no_such_parent"), idem).getStatusCode().value());

        // 有子项禁删 409；先把子项改回根级（parentItemValue 空串）
        ResponseEntity<String> reRoot = adminPatch(root.accessToken(),
                "/api/v1/admin/dicts/" + dictIdA + "/items/" + leafId,
                Map.of("parentItemValue", ""), null, idem);
        assertEquals(200, reRoot.getStatusCode().value(), "改回根级失败: " + reRoot.getBody());
        assertTrue(dataNode(reRoot).path("parentItemValue").isNull(), "空串应清除父项");

        // 未清父项前父项禁删（重新挂回再验证 409，随后删除子项）
        assertEquals(200, adminPatch(root.accessToken(),
                "/api/v1/admin/dicts/" + dictIdA + "/items/" + leafId,
                Map.of("parentItemValue", "grp"), null, idem).getStatusCode().value());
        long grpId = dataNode(grp).path("id").asLong();
        assertEquals(409, adminDelete(root.accessToken(),
                "/api/v1/admin/dicts/" + dictIdA + "/items/" + grpId, idem).getStatusCode().value());
        assertEquals(204, adminDelete(root.accessToken(),
                "/api/v1/admin/dicts/" + dictIdA + "/items/" + leafId, idem).getStatusCode().value());
        assertEquals(204, adminDelete(root.accessToken(),
                "/api/v1/admin/dicts/" + dictIdA + "/items/" + grpId, idem).getStatusCode().value());

        // 清理
        assertEquals(204, adminDelete(root.accessToken(), "/api/v1/admin/dicts/" + dictIdA, idem).getStatusCode().value());
        assertEquals(204, adminDelete(root.accessToken(), "/api/v1/admin/dicts/" + dictIdB, idem).getStatusCode().value());
    }

    // ---------- 2. 市场 options 返回迁移后的字典数据 ----------

    @Test
    void marketOptionsReflectMigratedDictData() {
        ResponseEntity<String> options = rest.exchange("/api/v1/metadata/options", HttpMethod.GET,
                new HttpEntity<>(ipHeaders()), String.class);
        assertEquals(200, options.getStatusCode().value());
        JsonNode taxonomies = dataNode(options).path("taxonomies");
        assertTrue(taxonomies.isObject(), "taxonomies 应为对象");

        // 9 组市场字典齐全，且不含管理类字典
        for (String code : MARKET_DICT_CODES) {
            assertTrue(taxonomies.has(code), "市场 options 缺少分组: " + code);
            assertTrue(taxonomies.path(code).size() > 0, "分组 " + code + " 不应为空");
        }
        assertFalse(taxonomies.has("sys_user_status"), "管理字典不应出现在市场 options");

        // framework 含迁移的 pytorch（label_en 随 V18 迁移，displayNameEn 可用）
        JsonNode pytorch = findOption(taxonomies.path("framework"), "pytorch");
        assertEquals("PyTorch", pytorch.path("displayName").asText());
        assertEquals("PyTorch", pytorch.path("displayNameEn").asText());
        assertEquals("active", pytorch.path("status").asText());

        // model_task 层级迁移正确：multimodal 为根、text-generation 挂 nlp
        JsonNode multimodal = findOption(taxonomies.path("model_task"), "multimodal");
        assertTrue(multimodal.path("parentKey").isNull() || multimodal.path("parentKey").asText().isBlank(),
                "multimodal 应为根级: " + multimodal);
        JsonNode textGen = findOption(taxonomies.path("model_task"), "text-generation");
        assertEquals("nlp", textGen.path("parentKey").asText(), "text-generation 应挂在 nlp 下");
    }

    // ---------- 3. 任务投影外键迁移正确性 ----------

    @Test
    void profileProjectionForeignKeysResolveToDictItems() throws Exception {
        Session owner = newUser("dhproj");
        String ns = userNamespaceId(owner);
        Map<String, Object> metadata = new HashMap<>();
        metadata.put("license", "MIT");
        metadata.put("task", "text-generation");
        metadata.put("framework", "pytorch");
        ResponseEntity<String> created = createRepo(owner, ns, unique("DhProj"), "public", null, metadata);
        assertEquals(201, created.getStatusCode().value(), "建库失败: " + created.getBody());
        String repoId = dataNode(created).path("id").asText();
        awaitLifecycleHttp(owner.accessToken(), repoId, "active");

        // model_profiles.task_value_id 指向 model_task/text-generation 的字典项（V18 外键切换）
        Integer hits = jdbc.queryForObject(
                "SELECT COUNT(*) FROM model_profiles mp "
                        + "JOIN sys_dict_item i ON i.id = mp.task_value_id "
                        + "JOIN sys_dict d ON d.id = i.dict_id "
                        + "WHERE mp.repository_id = (SELECT id FROM repositories WHERE public_id = ?) "
                        + "AND d.dict_code = 'model_task' AND i.item_value = 'text-generation'",
                Integer.class, UUID.fromString(repoId));
        assertEquals(1, hits, "任务投影外键应经 V18 迁移指向 sys_dict_item");

        // 新建仓库的任务检索不受迁移影响（facet 文本过滤仍命中）
        ResponseEntity<String> filtered = rest.exchange(
                "/api/v1/repositories?type=model&task=text-generation", HttpMethod.GET,
                new HttpEntity<>(ipHeaders()), String.class);
        assertEquals(200, filtered.getStatusCode().value());
        boolean found = false;
        for (JsonNode it : dataNode(filtered).path("items")) {
            if (repoId.equals(it.path("id").asText())) {
                found = true;
            }
        }
        assertTrue(found, "按任务过滤应命中新建仓库: " + dataNode(filtered));
    }

    // ---------- 助手 ----------

    private String createDict(Session root, String dictCode, String idem) {
        ResponseEntity<String> resp = adminPost(root.accessToken(), "/api/v1/admin/dicts",
                Map.of("dictCode", dictCode, "name", "层级测试-" + dictCode), idem);
        assertEquals(201, resp.getStatusCode().value(), "创建字典失败: " + resp.getBody());
        return dataNode(resp).path("id").asText();
    }

    private ResponseEntity<String> createItem(Session root, String dictId, Map<String, Object> body, String idem) {
        return adminPost(root.accessToken(), "/api/v1/admin/dicts/" + dictId + "/items", body, idem);
    }

    private static JsonNode findOption(JsonNode arr, String key) {
        for (JsonNode o : arr) {
            if (key.equals(o.path("key").asText())) {
                return o;
            }
        }
        throw new AssertionError("options 缺少键: " + key + " in " + arr);
    }

    private ResponseEntity<String> adminPost(String token, String path, Object body, String idem) {
        HttpHeaders h = bearer(token);
        h.setContentType(MediaType.APPLICATION_JSON);
        h.add("Idempotency-Key", idem);
        return rest.exchange(path, HttpMethod.POST, new HttpEntity<>(body, h), String.class);
    }

    private ResponseEntity<String> adminPatch(String token, String path, Object body,
                                              String ifMatch, String idem) {
        HttpHeaders h = bearer(token);
        h.setContentType(MediaType.APPLICATION_JSON);
        h.add("Idempotency-Key", idem);
        if (ifMatch != null) {
            h.add("If-Match", ifMatch);
        }
        return rest.exchange(path, HttpMethod.PATCH, new HttpEntity<>(body, h), String.class);
    }

    private ResponseEntity<String> adminDelete(String token, String path, String idem) {
        HttpHeaders h = bearer(token);
        h.add("Idempotency-Key", idem);
        return rest.exchange(path, HttpMethod.DELETE, new HttpEntity<>(h), String.class);
    }
}
