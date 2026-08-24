package com.modelhub.app;

import com.fasterxml.jackson.databind.JsonNode;
import com.modelhub.catalog.repo.GatedRequestRepository;
import com.modelhub.catalog.service.GatedAccessService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;

import java.time.OffsetDateTime;
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

    @Autowired
    private GatedAccessService gatedAccess;

    @Autowired
    private GatedRequestRepository gatedRequests;

    @Test
    void resourceTypesAndMetadataOptionsAnonymousReadable() {
        ResponseEntity<String> types = rest.exchange("/api/v1/resource-types", HttpMethod.GET,
                new HttpEntity<>(ipHeaders()), String.class);
        assertEquals(200, types.getStatusCode().value());
        // 契约 ResourceTypeListEnvelope：data 为对象，items 为数组
        JsonNode items = dataNode(types).path("items");
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

        // 重复 revoke 幂等：返回相同终态而非 409（04 §10），过期 If-Match 亦被接受
        ResponseEntity<String> revokeReplay = postJson(owner.accessToken(),
                basePath + "/" + requestId + ":revoke", Map.of(),
                Map.of("If-Match", etagOfVersion(approvedVersion)));
        assertEquals(200, revokeReplay.getStatusCode().value());
        assertEquals("revoked", dataNode(revokeReplay).path("status").asText());

        ResponseEntity<String> reApply = postJson(applicant.accessToken(), basePath,
                Map.of("reason", "重新申请"), null);
        assertEquals(201, reApply.getStatusCode().value());

        // 申请人撤回 pending 申请：契约无 withdrawn 状态，统一落到 revoked
        String newReqId = dataNode(reApply).path("id").asText();
        long newVersion = dataNode(reApply).path("version").asLong();
        ResponseEntity<String> selfWithdrawn = postJson(applicant.accessToken(),
                basePath + "/" + newReqId + ":revoke", Map.of(),
                Map.of("If-Match", etagOfVersion(newVersion)));
        assertEquals(200, selfWithdrawn.getStatusCode().value());
        assertEquals("revoked", dataNode(selfWithdrawn).path("status").asText());

        // 重复撤回同样幂等返回 revoked 终态
        ResponseEntity<String> withdrawReplay = postJson(applicant.accessToken(),
                basePath + "/" + newReqId + ":revoke", Map.of(),
                Map.of("If-Match", etagOfVersion(newVersion)));
        assertEquals(200, withdrawReplay.getStatusCode().value());
        assertEquals("revoked", dataNode(withdrawReplay).path("status").asText());
    }

    @Test
    void gatedGrantExpiryMarksRequestExpired() throws Exception {
        Session owner = newUser("gateexp");
        Session applicant = newUser("gateexpreq");
        String ns = userNamespaceId(owner);
        String repoId = dataNode(createRepo(owner, ns, unique("Gexp"), "public", true, null))
                .path("id").asText();
        awaitLifecycleHttp(owner.accessToken(), repoId, "active");

        String basePath = "/api/v1/repositories/" + repoId + "/access-requests";
        ResponseEntity<String> req = postJson(applicant.accessToken(), basePath,
                Map.of("reason", "短期评测"), null);
        assertEquals(201, req.getStatusCode().value());
        JsonNode requestData = dataNode(req);
        String requestId = requestData.path("id").asText();
        long reqVersion = requestData.path("version").asLong();

        // 批准 1 秒短 TTL grant
        ResponseEntity<String> approved = postJson(owner.accessToken(),
                basePath + "/" + requestId + ":approve",
                Map.of("grantExpiresAt", OffsetDateTime.now().plusSeconds(1).toString()),
                Map.of("If-Match", etagOfVersion(reqVersion)));
        assertEquals(200, approved.getStatusCode().value());
        assertEquals("approved", dataNode(approved).path("status").asText());

        // TTL 过期后由到期收敛将申请置为 expired（02 §4 服务端时间判定）
        Thread.sleep(2_000);
        gatedAccess.expireOverdue();

        ResponseEntity<String> mine = rest.exchange("/api/v1/me/access-requests", HttpMethod.GET,
                new HttpEntity<>(bearer(applicant.accessToken())), String.class);
        assertEquals(200, mine.getStatusCode().value());
        assertEquals("expired", dataNode(mine).path("items").get(0).path("status").asText());

        // 过期 grant 不再有效：申请人可立即重新申请
        ResponseEntity<String> reApply = postJson(applicant.accessToken(), basePath,
                Map.of("reason", "再次申请"), null);
        assertEquals(201, reApply.getStatusCode().value());
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

    // ---------- Task 8：access-requests keyset 游标分页（04 §6.2 无重复/无遗漏） ----------

    /** 维护者申请列表 limit=2 逐页遍历：5 条申请每条恰好一次、id 严格降序、末页 nextCursor 为 JSON null。 */
    @Test
    void gatedAccessRequestsCursorWalkNoDuplicateNoMiss() throws Exception {
        Session owner = newUser("gatewalk");
        String ns = userNamespaceId(owner);
        String repoId = dataNode(createRepo(owner, ns, unique("GWlk"), "public", true, null))
                .path("id").asText();
        awaitLifecycleHttp(owner.accessToken(), repoId, "active");
        String basePath = "/api/v1/repositories/" + repoId + "/access-requests";

        // 5 个不同申请人各建一条申请（同秒创建在旧的 createdAt 内存分页下会跨页重复/遗漏）
        Set<String> created = new HashSet<>();
        for (int i = 0; i < 5; i++) {
            Session applicant = newUser("gwapp" + i);
            ResponseEntity<String> req = postJson(applicant.accessToken(), basePath,
                    Map.of("reason", "walk-" + i), null);
            assertEquals(201, req.getStatusCode().value());
            created.add(dataNode(req).path("id").asText());
        }

        List<JsonNode> items = walkCursor(basePath + "?limit=2", owner.accessToken(), 2, null);
        assertEquals(5, items.size(), "串联所有页应覆盖全部 5 条申请");
        Set<String> seen = new HashSet<>();
        for (JsonNode item : items) {
            assertTrue(seen.add(item.path("id").asText()), "跨页出现重复申请");
        }
        assertEquals(created, seen, "每条申请应恰好出现一次（无遗漏）");
        assertInternalIdsStrictlyDescending(items);
    }

    /** /me/access-requests 跨仓库 limit=1 遍历 + status 过滤翻页；非法 status 仍 422。 */
    @Test
    void meAccessRequestsCursorWalkAcrossReposWithStatusFilter() throws Exception {
        Session applicant = newUser("mewalk");
        List<Session> owners = new ArrayList<>();
        List<String> repoIds = new ArrayList<>();
        for (int i = 0; i < 3; i++) {
            Session owner = newUser("meown" + i);
            String ns = userNamespaceId(owner);
            repoIds.add(dataNode(createRepo(owner, ns, unique("MeW"), "public", true, null))
                    .path("id").asText());
            awaitLifecycleHttp(owner.accessToken(), repoIds.get(i), "active");
            owners.add(owner);
        }

        // 同一申请人跨 3 个 gated 仓库各建一条申请
        List<String> createdIds = new ArrayList<>();
        long firstVersion = 0;
        for (int i = 0; i < repoIds.size(); i++) {
            ResponseEntity<String> req = postJson(applicant.accessToken(),
                    "/api/v1/repositories/" + repoIds.get(i) + "/access-requests",
                    Map.of("reason", "me-walk-" + i), null);
            assertEquals(201, req.getStatusCode().value());
            if (i == 0) {
                firstVersion = dataNode(req).path("version").asLong();
            }
            createdIds.add(dataNode(req).path("id").asText());
        }

        // limit=1 逐页遍历：3 条恰好各一次、id 严格降序、末页 nextCursor=null
        List<JsonNode> items = walkCursor("/api/v1/me/access-requests?limit=1",
                applicant.accessToken(), 1, null);
        assertEquals(3, items.size());
        Set<String> seen = new HashSet<>();
        for (JsonNode item : items) {
            assertTrue(seen.add(item.path("id").asText()), "跨页出现重复申请");
        }
        assertEquals(Set.copyOf(createdIds), seen, "每条申请应恰好出现一次（无遗漏）");
        assertInternalIdsStrictlyDescending(items);

        // 批准最早一条后：pending 过滤翻页剩 2 条，approved 过滤恰 1 条
        ResponseEntity<String> approved = postJson(owners.get(0).accessToken(),
                "/api/v1/repositories/" + repoIds.get(0) + "/access-requests/"
                        + createdIds.get(0) + ":approve",
                Map.of(), Map.of("If-Match", etagOfVersion(firstVersion)));
        assertEquals(200, approved.getStatusCode().value());

        List<JsonNode> pending = walkCursor("/api/v1/me/access-requests?status=pending&limit=1",
                applicant.accessToken(), 1, null);
        assertEquals(2, pending.size(), "pending 过滤翻页应只剩 2 条");
        for (JsonNode item : pending) {
            assertEquals("pending", item.path("status").asText());
        }
        assertInternalIdsStrictlyDescending(pending);

        List<JsonNode> approvedItems = walkCursor("/api/v1/me/access-requests?status=approved&limit=1",
                applicant.accessToken(), 1, null);
        assertEquals(1, approvedItems.size());
        assertEquals(createdIds.get(0), approvedItems.get(0).path("id").asText());

        // 非法 status（含已移除的 withdrawn）仍 422，MeController 枚举校验保持不变
        ResponseEntity<String> bad = rest.exchange("/api/v1/me/access-requests?status=withdrawn",
                HttpMethod.GET, new HttpEntity<>(bearer(applicant.accessToken())), String.class);
        assertEquals(422, bad.getStatusCode().value());
        assertEquals("VALIDATION_FAILED", errorCode(bad));
    }

    /** 翻页途中新申请写入：续读不重复不遗漏，迟到条目（id 最大）不会被重复补发。 */
    @Test
    void gatedAccessRequestsNoDuplicateWhenNewRequestArrivesMidWalk() throws Exception {
        Session owner = newUser("gateilw");
        String ns = userNamespaceId(owner);
        String repoId = dataNode(createRepo(owner, ns, unique("GIlw"), "public", true, null))
                .path("id").asText();
        awaitLifecycleHttp(owner.accessToken(), repoId, "active");
        String basePath = "/api/v1/repositories/" + repoId + "/access-requests";

        Set<String> created = new HashSet<>();
        for (int i = 0; i < 5; i++) {
            Session applicant = newUser("ilapp" + i);
            ResponseEntity<String> req = postJson(applicant.accessToken(), basePath,
                    Map.of("reason", "ilw-" + i), null);
            assertEquals(201, req.getStatusCode().value());
            created.add(dataNode(req).path("id").asText());
        }

        // 取走首页 2 条后，新申请人写入（新 id 最大，只可能落在已翻过的页区间）
        ResponseEntity<String> firstResp = rest.exchange(basePath + "?limit=2", HttpMethod.GET,
                new HttpEntity<>(bearer(owner.accessToken())), String.class);
        assertEquals(200, firstResp.getStatusCode().value());
        JsonNode page1 = dataNode(firstResp);
        assertEquals(2, page1.path("items").size());
        assertTrue(page1.path("nextCursor").isTextual(), "首页应携带 nextCursor");

        Session lateApplicant = newUser("illate");
        ResponseEntity<String> lateReq = postJson(lateApplicant.accessToken(), basePath,
                Map.of("reason", "late"), null);
        assertEquals(201, lateReq.getStatusCode().value());
        String lateId = dataNode(lateReq).path("id").asText();

        // 以首页游标续读翻完剩余 3 条后与首页合并：原始 5 条各出现一次，
        // 迟到写入（id 最大，位于已翻过区间）不会在续读页中重复补发
        List<JsonNode> resumed = walkCursor(basePath + "?limit=2", owner.accessToken(), 2,
                page1.path("nextCursor").asText());
        assertEquals(3, resumed.size(), "续读应恰好覆盖剩余 3 条原始申请");
        List<JsonNode> items = new ArrayList<>();
        page1.path("items").forEach(items::add);
        items.addAll(resumed);
        assertEquals(5, items.size(), "首页 + 续读合计应恰好覆盖原始 5 条");
        Set<String> seen = new HashSet<>();
        for (JsonNode item : items) {
            assertTrue(seen.add(item.path("id").asText()), "并发写入下跨页出现重复申请");
        }
        assertEquals(created, seen);
        assertTrue(!seen.contains(lateId), "迟到的申请（id 最大）不应在续读页中重复补发");
        assertInternalIdsStrictlyDescending(items);
    }

    // ---------- Task 8 工具 ----------

    /**
     * 从 startCursor（null 即首页）开始按 nextCursor 逐页遍历至末页，返回串联条目。
     * 断言：每页 1..limit 条、非末页 nextCursor 为非空串、末页显式携带 JSON null。
     */
    private List<JsonNode> walkCursor(String pathWithLimit, String token, int limit, String startCursor) {
        List<JsonNode> items = new ArrayList<>();
        String cursor = startCursor;
        for (int pages = 0; ; pages++) {
            assertTrue(pages < 50, "翻页次数异常，疑似游标环");
            ResponseEntity<String> resp = rest.exchange(
                    pathWithLimit + (cursor == null ? "" : "&cursor=" + cursor),
                    HttpMethod.GET, new HttpEntity<>(bearer(token)), String.class);
            assertEquals(200, resp.getStatusCode().value());
            JsonNode data = dataNode(resp);
            int size = data.path("items").size();
            assertTrue(size >= 1 && size <= limit, "页大小应在 1.." + limit + "，实际=" + size);
            data.path("items").forEach(items::add);
            JsonNode next = data.path("nextCursor");
            if (next.isNull()) {
                assertTrue(data.has("nextCursor"), "末页应显式携带 nextCursor:null");
                return items;
            }
            assertTrue(next.isTextual() && !next.asText().isBlank(), "非末页 nextCursor 应为非空串");
            cursor = next.asText();
        }
    }

    /** 响应条目（publicId）映射回内部自增 id，断言 keyset 输出严格降序（排序键与续读键一致）。 */
    private void assertInternalIdsStrictlyDescending(List<JsonNode> items) {
        Long prev = null;
        for (JsonNode item : items) {
            long id = gatedRequests.findByPublicId(UUID.fromString(item.path("id").asText()))
                    .orElseThrow().getId();
            if (prev != null) {
                assertTrue(prev > id, "条目应按内部 id 严格降序，prev=" + prev + " cur=" + id);
            }
            prev = id;
        }
    }
}
