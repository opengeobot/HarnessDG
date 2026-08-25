package com.modelhub.app;

import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.Test;
import org.springframework.test.context.TestPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.utility.DockerImageName;

import java.time.Duration;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * provisioning 故障注入（05 §8/§9.2）：Gitea 不可达时重试耗尽进入 failed，
 * 管理面 :retry 复活流程、:delete 强制终止。
 *
 * 隔离设计：本类上下文若与主测试上下文共用同一数据库，主上下文的 OutboxPoller
 * 会消费同一 outbox 事件（真实 Gitea “代劳”或争抢重试计数），故障场景失效且
 * 干扰主流程（删除事件被不可达 Gitea 的 poller 重试至死信）；因此使用独立
 * PostgreSQL 容器（failhub 库）+ 不可达 Gitea，经 {@link BaseIntegrationTest#infraOverrides}
 * 钩子从源头替换动态属性（@TestPropertySource 内联属性优先级低于 @DynamicPropertySource，
 * 无法覆盖数据源；内联 marker 仅用于区分上下文缓存键）。
 */
@TestPropertySource(properties = "modelhub.test.profile=fail-provisioning")
class CatalogProvisionFailureTest extends CatalogTestSupport {

    static final String FAIL_PG_HOST = "mh-tc-failpg";

    static final PostgreSQLContainer<?> FAIL_PG =
            new PostgreSQLContainer<>(DockerImageName.parse("postgres:16-alpine"))
                    .withDatabaseName("failhub").withUsername("test").withPassword("test")
                    .withCreateContainerCmdModifier(joinNet(FAIL_PG_HOST))
                    // 默认 JDBC 等待策略探测宿主映射端口（本机发布端口转发不稳定），改用日志等待
                    .waitingFor(Wait.forLogMessage(".*ready to accept connections.*", 2)
                            .withStartupTimeout(Duration.ofMinutes(3)))
                    .withReuse(true);

    static {
        FAIL_PG.start();
        infraOverrides = Map.of(
                "spring.datasource.url", jdbcUrlOf(FAIL_PG_HOST, "failhub") + "?stringtype=unspecified",
                "spring.datasource.username", "test",
                "spring.datasource.password", "test",
                "modelhub.gitea.base-url", "http://127.0.0.1:1",
                "modelhub.catalog.provision-max-retries", "1",
                "modelhub.catalog.poll-interval-ms", "100");
    }

    @Test
    void provisionFailureExhaustsRetriesThenAdminRetryAndDelete() throws Exception {
        Session owner = newUser("failprov");
        String ns = userNamespaceId(owner);
        String repoId = dataNode(createRepo(owner, ns, unique("Fail"), "public", null, null))
                .path("id").asText();
        UUID publicId = UUID.fromString(repoId);
        assertEquals("provisioning", dataNode(repoDetail(owner.accessToken(), repoId))
                .path("lifecycleStatus").asText());

        // Gitea 不可达 + max-retries=1 → 重试耗尽后 failed（DB 视角轮询）
        awaitEntityStatus(publicId, "failed");

        // 非管理员无权触达管理面 → 403/404
        int nonAdmin = postJson(owner.accessToken(),
                "/api/v1/admin/repositories/" + repoId + ":retry", Map.of(),
                Map.of("Idempotency-Key", UUID.randomUUID().toString())).getStatusCode().value();
        assertTrue(nonAdmin == 403 || nonAdmin == 404, "非管理员不应可调用管理面，实际=" + nonAdmin);

        Session root = login("platform-root", "Boot-Strap-1x");

        // admin retry：202 且回到 provisioning，最终再次 failed
        var retryResp = postJson(root.accessToken(),
                "/api/v1/admin/repositories/" + repoId + ":retry", Map.of(),
                Map.of("Idempotency-Key", UUID.randomUUID().toString()));
        assertEquals(202, retryResp.getStatusCode().value());
        JsonNode retryJob = dataNode(retryResp);
        assertEquals("repository.provisioning", retryJob.path("type").asText());
        assertTrue("queued".equals(retryJob.path("status").asText())
                || "running".equals(retryJob.path("status").asText()));
        awaitEntityStatus(publicId, "failed");

        // admin delete：202 → deleted（含 retentionUntil 设置）
        var deleteResp = postJson(root.accessToken(),
                "/api/v1/admin/repositories/" + repoId + ":delete", Map.of(),
                Map.of("Idempotency-Key", UUID.randomUUID().toString()));
        assertEquals(202, deleteResp.getStatusCode().value());
        assertEquals("repository.deletion", dataNode(deleteResp).path("type").asText());
        var deleted = awaitEntityStatus(publicId, "deleted");
        assertTrue(deleted.getRetentionUntil() != null, "deleted 必须设置 retentionUntil");

        // deleted 后 owner 普通流程不可见
        assertEquals(404, repoDetail(owner.accessToken(), repoId).getStatusCode().value());
    }
}
