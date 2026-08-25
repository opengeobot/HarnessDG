package com.modelhub.app;

import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.TestPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.utility.DockerImageName;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 治理集成测试（04 §4.1/§8、07 SEC-05、GOV-002）：
 * 审计查询分页与过滤、NDJSON 流式导出（不泄漏敏感串）、角色管控（普通用户 403/匿名 401）、
 * admin :retry 审计闭环（只追加语义）。
 *
 * 隔离设计：admin :retry 需 failed 状态仓库，依赖不可达 Gitea 故障注入；与主测试上下文
 * 共用数据库会互相争抢 Outbox 事件（见 CatalogProvisionFailureTest 说明），故使用独立
 * PostgreSQL（govhub）+ 不可达 Gitea，经 {@link BaseIntegrationTest#infraOverrides} 钩子
 * 从源头替换动态属性。
 */
@TestPropertySource(properties = "modelhub.test.profile=governance")
class GovernanceFlowTest extends CatalogTestSupport {

    static final String GOV_PG_HOST = "mh-tc-govpg";

    static final PostgreSQLContainer<?> GOV_PG =
            new PostgreSQLContainer<>(DockerImageName.parse("postgres:16-alpine"))
                    .withDatabaseName("govhub").withUsername("test").withPassword("test")
                    .withCreateContainerCmdModifier(joinNet(GOV_PG_HOST))
                    // 默认 JDBC 等待策略探测宿主映射端口（本机发布端口转发不稳定），改用日志等待
                    .waitingFor(Wait.forLogMessage(".*ready to accept connections.*", 2)
                            .withStartupTimeout(Duration.ofMinutes(3)))
                    .withReuse(true);

    static {
        GOV_PG.start();
        infraOverrides = Map.of(
                "spring.datasource.url", jdbcUrlOf(GOV_PG_HOST, "govhub") + "?stringtype=unspecified",
                "spring.datasource.username", "test",
                "spring.datasource.password", "test",
                "modelhub.gitea.base-url", "http://127.0.0.1:1",
                "modelhub.catalog.provision-max-retries", "1",
                "modelhub.catalog.poll-interval-ms", "100");
    }

    @Test
    void auditLogsQueryPagedAndFiltered() {
        Session root = login("platform-root", "Boot-Strap-1x");
        Session user = newUser("govaudituser");

        ResponseEntity<String> page = auditLogs(root.accessToken(), "?limit=5");
        assertEquals(200, page.getStatusCode().value());
        JsonNode items = dataNode(page).path("items");
        assertTrue(items.size() > 0 && items.size() <= 5, "items 应非空且不超过 limit: " + items);
        assertTrue(items.get(0).hasNonNull("traceId"));

        // actor + action 过滤命中（user.register 由注册流程写入）
        ResponseEntity<String> filtered = auditLogs(root.accessToken(),
                "?limit=20&actor=" + user.username() + "&action=user.register");
        assertEquals(200, filtered.getStatusCode().value());
        JsonNode fItems = dataNode(filtered).path("items");
        assertTrue(fItems.size() >= 1, "应至少命中一条 user.register 审计: " + fItems);
        for (JsonNode it : fItems) {
            assertEquals("user.register", it.path("action").asText());
            assertEquals(user.username(), it.path("actor").asText());
        }
    }

    @Test
    void auditLogsNdjsonExport() throws Exception {
        Session root = login("platform-root", "Boot-Strap-1x");
        // String 提取器不支持 application/x-ndjson，用 byte[] 提取（ByteArrayHttpMessageConverter 支持全部媒体类型）
        ResponseEntity<byte[]> resp = rest.exchange("/api/v1/admin/audit-logs?format=ndjson",
                HttpMethod.GET, new HttpEntity<>(bearer(root.accessToken())), byte[].class);
        assertEquals(200, resp.getStatusCode().value());
        assertTrue(String.valueOf(resp.getHeaders().getContentType()).contains("application/x-ndjson"),
                "content-type 应为 application/x-ndjson，实际=" + resp.getHeaders().getContentType());

        assertNotNull(resp.getBody());
        String body = new String(resp.getBody(), StandardCharsets.UTF_8);
        String[] lines = body.split("\n");
        assertTrue(lines.length >= 1, "ndjson 至少一行");
        for (String line : lines) {
            if (line.isBlank()) {
                continue;
            }
            JsonNode row = JSON.readTree(line);
            assertTrue(row.hasNonNull("traceId"), "每行应含 traceId: " + line);
            assertTrue(row.hasNonNull("createdAt"), "每行应含 createdAt: " + line);
            assertTrue(row.path("action").isTextual(), "每行应含 action: " + line);
            // SEC-05：审计导出不得泄漏 token/预签名 URL 敏感串
            assertFalse(line.contains("mh_"), "审计行不得含 token 敏感串: " + line);
            assertFalse(line.contains("X-Amz-"), "审计行不得含预签名 URL: " + line);
        }
    }

    @Test
    void auditLogsForbidden() {
        Session user = newUser("govauditforbidden");
        ResponseEntity<String> forbidden = auditLogs(user.accessToken(), "");
        assertEquals(403, forbidden.getStatusCode().value());
        assertEquals("FORBIDDEN", errorCode(forbidden));

        ResponseEntity<String> anon = auditLogs(null, "");
        assertEquals(401, anon.getStatusCode().value());
        assertEquals("UNAUTHENTICATED", errorCode(anon));
    }

    @Test
    void adminActionWritesAudit() throws Exception {
        Session owner = newUser("govauditretry");
        String repoId = dataNode(createRepo(owner, userNamespaceId(owner), unique("govfail"),
                "public", null, null)).path("id").asText();
        // Gitea 不可达 + max-retries=1 → 重试耗尽进入 failed
        awaitEntityStatus(UUID.fromString(repoId), "failed");

        Session root = login("platform-root", "Boot-Strap-1x");
        ResponseEntity<String> retry = postJson(root.accessToken(),
                "/api/v1/admin/repositories/" + repoId + ":retry", Map.of(),
                Map.of("Idempotency-Key", UUID.randomUUID().toString()));
        assertEquals(202, retry.getStatusCode().value());

        // GOV-002 闭环：admin 操作立即落审计（REQUIRES_NEW，只追加）
        ResponseEntity<String> q = auditLogs(root.accessToken(),
                "?action=repository.admin_retry&limit=20");
        assertEquals(200, q.getStatusCode().value());
        JsonNode items = dataNode(q).path("items");
        assertTrue(items.size() >= 1, "应命中 repository.admin_retry 审计: " + items);
        assertEquals("repository.admin_retry", items.get(0).path("action").asText());
        assertEquals("accepted", items.get(0).path("result").asText());
        assertEquals("repository:" + repoId, items.get(0).path("resource").asText());
    }

    private ResponseEntity<String> auditLogs(String token, String query) {
        HttpHeaders h = token == null ? ipHeaders() : bearer(token);
        return rest.exchange("/api/v1/admin/audit-logs" + query, HttpMethod.GET,
                new HttpEntity<>(h), String.class);
    }
}
