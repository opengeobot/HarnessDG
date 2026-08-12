package com.modelhub.app;

import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * catalog 核心流程集成测试（04 §2/§3/§6、05 §8/§9.2）：
 * provisioning Saga、条件更新、删除/恢复状态机、列表分页、gated 流程。
 */
class CatalogIntegrationTest extends CatalogTestSupport {

    @Test
    void resourceTypesAndMetadataOptionsAnonymousReadable() {
        ResponseEntity<String> types = rest.exchange("/api/v1/resource-types", HttpMethod.GET,
                new HttpEntity<>(ipHeaders()), String.class);
        assertEquals(200, types.getStatusCode().value());
        JsonNode items = dataNode(types);
        assertTrue(items.isArray() && items.size() >= 3, "应至少注册 model/dataset/studio 三类");

        ResponseEntity<String> options = rest.exchange("/api/v1/metadata/options", HttpMethod.GET,
                new HttpEntity<>(ipHeaders()), String.class);
        assertEquals(200, options.getStatusCode().value());
        JsonNode taxonomies = dataNode(options).path("taxonomies");
        assertTrue(taxonomies.isObject() && taxonomies.size() > 0, "metadata options 应返回受控词表");

        ResponseEntity<String> schema = rest.exchange("/api/v1/resource-types/model/schema", HttpMethod.GET,
                new HttpEntity<>(ipHeaders()), String.class);
        assertEquals(200, schema.getStatusCode().value());
        assertEquals("model", dataNode(schema).path("typeKey").asText());
    }

    @Test
    void createProvisionsToActiveAndDuplicateCaseInsensitive409() throws Exception {
        Session owner = newUser("prov");
        String ns = userNamespaceId(owner);
        String name = unique("Repo");

        ResponseEntity<String> created = createRepo(owner, ns, name, "public", null, null);
        assertEquals(201, created.getStatusCode().value());
        JsonNode data = dataNode(created);
        assertEquals("provisioning", data.path("lifecycleStatus").asText());
        String repoId = data.path("id").asText();

        JsonNode active = awaitLifecycleHttp(owner.accessToken(), repoId, "active");
        assertEquals("public", active.path("visibility").asText());

        // 大小写不同的重名必须 409（normalizedName 唯一约束）
        ResponseEntity<String> dup = createRepo(owner, ns, name.toUpperCase(), "public", null, null);
        assertEquals(409, dup.getStatusCode().value());
        assertEquals("CONFLICT", errorCode(dup));
    }

    @Test
    void patchRequiresIfMatchAndBumpsVersion() throws Exception {
        Session owner = newUser("patch");
        String ns = userNamespaceId(owner);
        String repoId = dataNode(createRepo(owner, ns, unique("Etag"), "public", null, null))
                .path("id").asText();
        JsonNode active = awaitLifecycleHttp(owner.accessToken(), repoId, "active");
        long version = active.path("version").asLong();

        ResponseEntity<String> noHeader = patchRepo(owner.accessToken(), repoId,
                Map.of("displayName", "X"), null);
        assertEquals(400, noHeader.getStatusCode().value());

        ResponseEntity<String> wrong = patchRepo(owner.accessToken(), repoId,
                Map.of("displayName", "X"), "\"deadbeef\"");
        assertEquals(412, wrong.getStatusCode().value());

        ResponseEntity<String> ok = patchRepo(owner.accessToken(), repoId,
                Map.of("displayName", "NewName"), etagOfVersion(version));
        assertEquals(200, ok.getStatusCode().value());
        assertEquals("NewName", dataNode(ok).path("displayName").asText());
        assertEquals(version + 1, dataNode(ok).path("version").asLong());

        // metadata 更新缺 metadataSchemaVersion → 422 dependentRequired
        ResponseEntity<String> missingSchema = patchRepo(owner.accessToken(), repoId,
                Map.of("metadata", Map.of("license", "Apache-2.0")), etagOfVersion(version + 1));
        assertEquals(422, missingSchema.getStatusCode().value());
        assertEquals("METADATA_SCHEMA_INVALID", errorCode(missingSchema));
    }

    @Test
    void deleteAndRestoreStateMachineWithIdempotentReplay() throws Exception {
        Session owner = newUser("delres");
        String ns = userNamespaceId(owner);
        String repoId = dataNode(createRepo(owner, ns, unique("Sag"), "public", null, null))
                .path("id").asText();
        JsonNode active = awaitLifecycleHttp(owner.accessToken(), repoId, "active");
        UUID publicId = UUID.fromString(repoId);

        // DELETE 缺 If-Match → 400
        ResponseEntity<String> noMatch = deleteRepo(owner.accessToken(), repoId, UUID.randomUUID().toString(), null);
        assertEquals(400, noMatch.getStatusCode().value());

        String idemKey = UUID.randomUUID().toString();
        ResponseEntity<String> accepted = deleteRepo(owner.accessToken(), repoId, idemKey,
                etagOfVersion(active.path("version").asLong()));
        assertEquals(202, accepted.getStatusCode().value());
        String jobId = dataNode(accepted).path("id").asText();
        assertEquals("repository.deletion", dataNode(accepted).path("type").asText());

        // 同 key 重放返回首次记录的响应（幂等）
        ResponseEntity<String> replay = deleteRepo(owner.accessToken(), repoId, idemKey,
                etagOfVersion(active.path("version").asLong()));
        assertEquals(202, replay.getStatusCode().value());
        assertEquals(jobId, dataNode(replay).path("id").asText());

        awaitEntityStatus(publicId, "deleted");
        // deleted 后普通流程不可见（含 owner）→ 404
        assertEquals(404, repoDetail(owner.accessToken(), repoId).getStatusCode().value());

        ResponseEntity<String> restored = restoreRepo(owner.accessToken(), repoId, UUID.randomUUID().toString());
        assertEquals(202, restored.getStatusCode().value());
        assertEquals("repository.restore", dataNode(restored).path("type").asText());
        awaitEntityStatus(publicId, "active");
        assertEquals(200, repoDetail(owner.accessToken(), repoId).getStatusCode().value());
    }

    @Test
    void listPaginationNoDuplicateNoMiss() throws Exception {
        Session owner = newUser("paging");
        String ns = userNamespaceId(owner);
        String prefix = unique("pg");
        List<String> ids = new ArrayList<>();
        for (int i = 0; i < 7; i++) {
            String repoId = dataNode(createRepo(owner, ns, prefix + "-" + i, "public", null, null))
                    .path("id").asText();
            ids.add(repoId);
            awaitLifecycleHttp(owner.accessToken(), repoId, "active");
        }

        // 匿名 + keyword 过滤，page 模式逐页收集
        Set<String> collected = new HashSet<>();
        int totalSeen = 0;
        for (int page = 1; page <= 3; page++) {
            ResponseEntity<String> resp = rest.exchange(
                    "/api/v1/repositories?keyword=" + prefix + "&pageSize=3&page=" + page,
                    HttpMethod.GET, new HttpEntity<>(ipHeaders()), String.class);
            assertEquals(200, resp.getStatusCode().value());
            JsonNode data = dataNode(resp);
            assertEquals(7, data.path("total").asLong());
            for (JsonNode item : data.path("items")) {
                assertTrue(collected.add(item.path("id").asText()), "分页出现重复条目");
                totalSeen++;
            }
        }
        assertEquals(7, totalSeen);
        assertTrue(collected.containsAll(ids));
    }

    @Test
    void unknownSortRejected() {
        ResponseEntity<String> resp = rest.exchange("/api/v1/repositories?sort=bogus-key",
                HttpMethod.GET, new HttpEntity<>(ipHeaders()), String.class);
        assertEquals(400, resp.getStatusCode().value());
        assertEquals("VALIDATION_FAILED", errorCode(resp));
    }

    @Test
    void resolveEndpointRespectsVisibility() throws Exception {
        Session owner = newUser("resolve");
        String ns = userNamespaceId(owner);
        String pubName = unique("Pub");
        String privName = unique("Priv");
        String pubId = dataNode(createRepo(owner, ns, pubName, "public", null, null)).path("id").asText();
        String privId = dataNode(createRepo(owner, ns, privName, "private", null, null)).path("id").asText();
        awaitLifecycleHttp(owner.accessToken(), pubId, "active");
        awaitLifecycleHttp(owner.accessToken(), privId, "active");

        ResponseEntity<String> anon = rest.exchange(
                "/api/v1/repositories/resolve/model/" + owner.username() + "/" + pubName,
                HttpMethod.GET, new HttpEntity<>(ipHeaders()), String.class);
        assertEquals(200, anon.getStatusCode().value());
        assertNotNull(anon.getHeaders().getFirst("ETag"));
        assertEquals(pubId, dataNode(anon).path("id").asText());

        // 私有仓库匿名 resolve → 404（防枚举）
        ResponseEntity<String> anonPriv = rest.exchange(
                "/api/v1/repositories/resolve/model/" + owner.username() + "/" + privName,
                HttpMethod.GET, new HttpEntity<>(ipHeaders()), String.class);
        assertEquals(404, anonPriv.getStatusCode().value());

        // owner 可 resolve 私有仓库
        HttpHeaders h = bearer(owner.accessToken());
        ResponseEntity<String> ownerPriv = rest.exchange(
                "/api/v1/repositories/resolve/model/" + owner.username() + "/" + privName,
                HttpMethod.GET, new HttpEntity<>(h), String.class);
        assertEquals(200, ownerPriv.getStatusCode().value());
    }

    @Test
    void gatedFlowRequestApproveRevokeAndConflicts() throws Exception {
        Session owner = newUser("gatedown");
        Session applicant = newUser("gatedreq");
        String ns = userNamespaceId(owner);
        String repoId = dataNode(createRepo(owner, ns, unique("Gate"), "public", true, null))
                .path("id").asText();
        awaitLifecycleHttp(owner.accessToken(), repoId, "active");

        String basePath = "/api/v1/repositories/" + repoId + "/access-requests";
        ResponseEntity<String> req = postJson(applicant.accessToken(), basePath,
                Map.of("reason", "需要评测该模型"), null);
        assertEquals(201, req.getStatusCode().value());
        JsonNode requestData = dataNode(req);
        assertEquals("pending", requestData.path("status").asText());
        long reqVersion = requestData.path("version").asLong();
        String requestId = requestData.path("id").asText();

        // 重复申请 → 409
        ResponseEntity<String> dupReq = postJson(applicant.accessToken(), basePath,
                Map.of("reason", "again"), null);
        assertEquals(409, dupReq.getStatusCode().value());

        // 批准缺 If-Match → 400；批准后建立 grant
        String approvePath = basePath + "/" + requestId + ":approve";
        ResponseEntity<String> noMatch = postJson(owner.accessToken(), approvePath, Map.of(), null);
        assertEquals(400, noMatch.getStatusCode().value());

        ResponseEntity<String> approved = postJson(owner.accessToken(), approvePath, Map.of(),
                Map.of("If-Match", etagOfVersion(reqVersion)));
        assertEquals(200, approved.getStatusCode().value());
        assertEquals("approved", dataNode(approved).path("status").asText());
        assertNotNull(dataNode(approved).path("grantExpiresAt").asText());

        // 已持有 grant 再申请 → 409
        ResponseEntity<String> withGrant = postJson(applicant.accessToken(), basePath,
                Map.of("reason", "again2"), null);
        assertEquals(409, withGrant.getStatusCode().value());

        // 维护者列表与申请人 /me 视图
        ResponseEntity<String> list = rest.exchange(basePath, HttpMethod.GET,
                new HttpEntity<>(bearer(owner.accessToken())), String.class);
        assertEquals(200, list.getStatusCode().value());
        assertEquals(1, dataNode(list).path("items").size());

        HttpHeaders meHeaders = bearer(applicant.accessToken());
        ResponseEntity<String> mine = rest.exchange("/api/v1/me/access-requests", HttpMethod.GET,
                new HttpEntity<>(meHeaders), String.class);
        assertEquals(200, mine.getStatusCode().value());
        assertEquals("approved", dataNode(mine).path("items").get(0).path("status").asText());

        // 撤销 grant 后可重新申请
        long approvedVersion = dataNode(approved).path("version").asLong();
        ResponseEntity<String> revoked = postJson(owner.accessToken(),
                basePath + "/" + requestId + ":revoke", Map.of(),
                Map.of("If-Match", etagOfVersion(approvedVersion)));
        assertEquals(200, revoked.getStatusCode().value());
        assertEquals("revoked", dataNode(revoked).path("status").asText());

        ResponseEntity<String> reApply = postJson(applicant.accessToken(), basePath,
                Map.of("reason", "重新申请"), null);
        assertEquals(201, reApply.getStatusCode().value());

        // 申请人撤回 pending 申请
        String newReqId = dataNode(reApply).path("id").asText();
        long newVersion = dataNode(reApply).path("version").asLong();
        ResponseEntity<String> withdrawn = postJson(applicant.accessToken(),
                basePath + "/" + newReqId + ":revoke", Map.of(),
                Map.of("If-Match", etagOfVersion(newVersion)));
        assertEquals(200, withdrawn.getStatusCode().value());
        assertEquals("withdrawn", dataNode(withdrawn).path("status").asText());
    }

    @Test
    void gatedRequiresPublicVisibility() {
        Session owner = newUser("gatepriv");
        String ns = userNamespaceId(owner);
        ResponseEntity<String> resp = createRepo(owner, ns, unique("Bad"), "private", true, null);
        assertEquals(422, resp.getStatusCode().value());
        assertEquals("METADATA_SCHEMA_INVALID", errorCode(resp));
    }

    @Test
    void metadataSchemaViolationRejected422() {
        Session owner = newUser("schema");
        String ns = userNamespaceId(owner);
        // model schema required=[license]，缺失即 422
        ResponseEntity<String> resp = createRepo(owner, ns, unique("NoLic"), "public", null, Map.of());
        assertEquals(422, resp.getStatusCode().value());
        assertEquals("METADATA_SCHEMA_INVALID", errorCode(resp));
        assertNotEquals("", errorCode(resp));
    }
}
