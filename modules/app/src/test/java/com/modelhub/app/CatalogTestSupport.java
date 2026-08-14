package com.modelhub.app;

import com.fasterxml.jackson.databind.JsonNode;
import com.modelhub.catalog.domain.RepositoryEntity;
import com.modelhub.catalog.repo.RepositoryRepository;
import com.modelhub.identity.domain.NamespaceEntity;
import com.modelhub.identity.repo.NamespaceRepository;
import com.modelhub.identity.repo.OrganizationRepository;
import com.modelhub.identity.repo.UserRepository;
import org.junit.jupiter.api.Assertions;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * catalog 集成测试公共助手：创建仓库/轮询生命周期/条件更新请求构造。
 */
public abstract class CatalogTestSupport extends BaseIntegrationTest {

    @Autowired
    protected NamespaceRepository namespaces;

    @Autowired
    protected OrganizationRepository organizations;

    @Autowired
    protected UserRepository users;

    @Autowired
    protected RepositoryRepository repositoryRepo;

    protected Session newUser(String prefix) {
        return register(unique(prefix), "Passw0rd-9x");
    }

    /** 注册用户即拥有同名个人 namespace（阶段 1 语义）。 */
    protected String userNamespaceId(Session s) {
        NamespaceEntity ns = namespaces.findBySlugIgnoreCase(s.username()).orElseThrow();
        return ns.getPublicId().toString();
    }

    protected ResponseEntity<String> createRepo(Session owner, String nsId, String name,
                                                String visibility, Boolean gated, Map<String, Object> metadata) {
        Map<String, Object> body = new HashMap<>();
        body.put("namespaceId", nsId);
        body.put("type", "model");
        body.put("metadataSchemaVersion", 1);
        body.put("name", name);
        body.put("visibility", visibility);
        if (gated != null) {
            body.put("gated", gated);
        }
        body.put("metadata", metadata == null ? Map.of("license", "MIT") : metadata);
        HttpHeaders h = bearer(owner.accessToken());
        h.setContentType(MediaType.APPLICATION_JSON);
        h.add("Idempotency-Key", UUID.randomUUID().toString());
        h.add("X-Test-Client-Ip", randomIp());
        return rest.exchange("/api/v1/repositories", HttpMethod.POST, new HttpEntity<>(body, h), String.class);
    }

    protected ResponseEntity<String> repoDetail(String token, String repoId) {
        HttpHeaders h = token == null ? ipHeaders() : bearer(token);
        return rest.exchange("/api/v1/repositories/" + repoId, HttpMethod.GET, new HttpEntity<>(h), String.class);
    }

    protected ResponseEntity<String> patchRepo(String token, String repoId, Map<String, Object> body, String ifMatch) {
        HttpHeaders h = bearer(token);
        h.setContentType(MediaType.APPLICATION_JSON);
        h.add("X-Test-Client-Ip", randomIp());
        if (ifMatch != null) {
            h.add("If-Match", ifMatch);
        }
        return rest.exchange("/api/v1/repositories/" + repoId, HttpMethod.PATCH, new HttpEntity<>(body, h), String.class);
    }

    protected ResponseEntity<String> deleteRepo(String token, String repoId, String idempotencyKey, String ifMatch) {
        HttpHeaders h = bearer(token);
        h.add("Idempotency-Key", idempotencyKey);
        h.add("X-Test-Client-Ip", randomIp());
        if (ifMatch != null) {
            h.add("If-Match", ifMatch);
        }
        return rest.exchange("/api/v1/repositories/" + repoId, HttpMethod.DELETE, new HttpEntity<>(h), String.class);
    }

    protected ResponseEntity<String> restoreRepo(String token, String repoId, String idempotencyKey) {
        HttpHeaders h = bearer(token);
        h.add("Idempotency-Key", idempotencyKey);
        h.add("X-Test-Client-Ip", randomIp());
        return rest.exchange("/api/v1/repositories/" + repoId + ":restore", HttpMethod.POST,
                new HttpEntity<>(h), String.class);
    }

    protected ResponseEntity<String> postJson(String token, String path, Object body, Map<String, String> extraHeaders) {
        HttpHeaders h = token == null ? ipHeaders() : bearer(token);
        h.setContentType(MediaType.APPLICATION_JSON);
        if (extraHeaders != null) {
            extraHeaders.forEach(h::add);
        }
        return rest.exchange(path, HttpMethod.POST, new HttpEntity<>(body, h), String.class);
    }

    /** 经 HTTP 轮询仓库 lifecycle（仅对可见主体有效；deleted 等不可见状态用 {@link #awaitEntityStatus}）。 */
    protected JsonNode awaitLifecycleHttp(String token, String repoId, String expected) throws Exception {
        long deadline = System.currentTimeMillis() + 60_000;
        String last = "";
        while (System.currentTimeMillis() < deadline) {
            ResponseEntity<String> resp = repoDetail(token, repoId);
            if (resp.getStatusCode().is2xxSuccessful() && resp.getBody() != null) {
                JsonNode data = JSON.readTree(resp.getBody()).path("data");
                last = data.path("lifecycleStatus").asText();
                if (expected.equals(last)) {
                    return data;
                }
            }
            Thread.sleep(300);
        }
        Assertions.fail("等待 lifecycle=" + expected + " 超时，最后状态=" + last);
        return null;
    }

    /** 直接经 DB 轮询实体状态（绕过授权可见性）。 */
    protected RepositoryEntity awaitEntityStatus(UUID publicId, String expected) throws InterruptedException {
        long deadline = System.currentTimeMillis() + 60_000;
        String last = "";
        while (System.currentTimeMillis() < deadline) {
            Optional<RepositoryEntity> repo = repositoryRepo.findByPublicId(publicId);
            if (repo.isPresent()) {
                last = repo.get().getLifecycleStatus();
                if (expected.equals(last)) {
                    return repo.get();
                }
            } else {
                last = "<absent>";
            }
            Thread.sleep(300);
        }
        Assertions.fail("等待实体 lifecycle=" + expected + " 超时，最后状态=" + last);
        return null;
    }

    /** 客户端重算 ETag（与 shared ETags.ofVersion 同规则）。 */
    protected static String etagOfVersion(long version) {
        return "\"" + Long.toHexString(version + 0x5F000000L) + "\"";
    }

    /** 读取详情 stats 单字段（无等待，用于断言置脏/重建后的即时值）。 */
    protected long statsValue(String token, String repoId, String field) {
        JsonNode stats = dataNode(repoDetail(token, repoId)).path("stats");
        return stats.path(field).asLong(-1);
    }

    /** 轮询详情 stats 直至字段收敛（Outbox 异步重算，测试 poll-interval=200ms，超时 15s）。 */
    protected void awaitStats(String token, String repoId, String field, long expected) throws Exception {
        long deadline = System.currentTimeMillis() + 15_000;
        long last = -1;
        while (System.currentTimeMillis() < deadline) {
            last = statsValue(token, repoId, field);
            if (expected == last) {
                return;
            }
            Thread.sleep(300);
        }
        Assertions.fail("等待 stats." + field + "=" + expected + " 超时，最后值=" + last);
    }

    protected static String dataOf(ResponseEntity<String> resp) {
        try {
            return JSON.readTree(resp.getBody()).path("data").toString();
        } catch (Exception e) {
            throw new IllegalStateException("解析响应失败: " + resp.getBody(), e);
        }
    }

    protected static JsonNode dataNode(ResponseEntity<String> resp) {
        try {
            return JSON.readTree(resp.getBody()).path("data");
        } catch (Exception e) {
            throw new IllegalStateException("解析响应失败: " + resp.getBody(), e);
        }
    }

    protected static String randomIp() {
        return "10.9." + (int) (Math.random() * 250) + "." + (int) (Math.random() * 250);
    }
}
