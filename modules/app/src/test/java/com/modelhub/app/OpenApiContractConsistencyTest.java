package com.modelhub.app;

import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.yaml.snakeyaml.Yaml;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * OpenAPI 契约一致性测试（T18）：以 prd/v1/contracts/openapi-v1.yaml 为基线，
 * 通过真实 API 调用比对响应结构与 components.schemas 的声明（必填字段齐全、
 * 枚举取值合法、不携带契约未声明的字段），并校验契约 paths 覆盖实现的关键端点。
 *
 * 契约文件位于 HarnessDG 仓库之外（../prd/v1/contracts）。本地运行时从
 * surefire 工作目录（modules/app）向上逐级解析；CI 只检出 HarnessDG、契约
 * 不可得时经 JUnit assumption 整体跳过，不阻断流水线。
 */
class OpenApiContractConsistencyTest extends CatalogTestSupport {

    private static final String CONTRACT_NAME = "openapi-v1.yaml";

    /**
     * 已知实现偏差：响应携带但 v1 契约基线未声明的字段（契约 schema 未设
     * additionalProperties:false，语义上不违约）。登记在此以拦截新增漂移；
     * 契约补齐声明或实现收敛后应同步移除。
     */
    private static final Map<String, Set<String>> KNOWN_UNDECLARED_FIELDS = Map.of(
            "User", Set.of("createdAt"),
            "Organization", Set.of("description", "etag", "createdAt"));

    private static Map<String, Object> contract;

    @BeforeAll
    static void loadContract() throws IOException {
        Path path = locateContract();
        assumeTrue(path != null, "OpenAPI 契约 prd/v1/contracts/" + CONTRACT_NAME
                + " 不可得（HarnessDG 为独立 git 仓库，CI 检出内无 prd/）——跳过契约一致性检查");
        try (InputStream in = Files.newInputStream(path)) {
            contract = new Yaml().load(in);
        }
        assertNotNull(contract, "契约 yaml 解析结果为空: " + path);
        assertNotNull(paths(), "契约缺少 paths 对象");
    }

    /** 从 surefire 工作目录（modules/app）起向上逐级查找 prd/v1/contracts/openapi-v1.yaml。 */
    private static Path locateContract() {
        for (Path base = Paths.get("").toAbsolutePath(); base != null; base = base.getParent()) {
            Path candidate = base.resolve("prd").resolve("v1").resolve("contracts").resolve(CONTRACT_NAME);
            if (Files.isRegularFile(candidate)) {
                return candidate;
            }
        }
        return null;
    }

    // ---------- 契约导航 ----------

    @SuppressWarnings("unchecked")
    private static Map<String, Object> paths() {
        return (Map<String, Object>) contract.get("paths");
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> schema(String name) {
        Map<String, Object> components = (Map<String, Object>) contract.get("components");
        Map<String, Object> schemas = (Map<String, Object>) components.get("schemas");
        Map<String, Object> schema = (Map<String, Object>) schemas.get(name);
        assertNotNull(schema, "契约缺少 schema: " + name);
        return schema;
    }

    @SuppressWarnings("unchecked")
    private static List<String> requiredOf(String schemaName) {
        Object required = schema(schemaName).get("required");
        return required == null ? List.of() : (List<String>) required;
    }

    @SuppressWarnings("unchecked")
    private static Set<String> propertiesOf(String schemaName) {
        Object props = schema(schemaName).get("properties");
        return props == null ? Set.of() : ((Map<String, Object>) props).keySet();
    }

    @SuppressWarnings("unchecked")
    private static List<String> enumOf(String schemaName, String property) {
        Map<String, Object> props = (Map<String, Object>) schema(schemaName).get("properties");
        Map<String, Object> prop = (Map<String, Object>) props.get(property);
        Object anEnum = prop.get("enum");
        assertNotNull(anEnum, "契约 " + schemaName + "." + property + " 未声明 enum");
        return (List<String>) anEnum;
    }

    private static Set<String> fieldNames(JsonNode node) {
        Set<String> names = new HashSet<>();
        node.fieldNames().forEachRemaining(names::add);
        return names;
    }

    private static void assertRequiredPresent(JsonNode data, String schemaName, String context) {
        for (String field : requiredOf(schemaName)) {
            assertTrue(data.hasNonNull(field),
                    context + ": 契约必填字段 " + schemaName + "." + field + " 缺失或为 null");
        }
    }

    private static void assertNoUndeclaredFields(JsonNode data, String schemaName, String context) {
        Set<String> extra = fieldNames(data);
        extra.removeAll(propertiesOf(schemaName));
        extra.removeAll(KNOWN_UNDECLARED_FIELDS.getOrDefault(schemaName, Set.of()));
        assertTrue(extra.isEmpty(), context + ": 响应携带契约 " + schemaName + " 未声明的字段 " + extra
                + "（有意扩展请更新契约，或在 KNOWN_UNDECLARED_FIELDS 登记偏差）");
    }

    // ---------- 1. User schema vs /auth/me ----------

    @Test
    void userSchemaConsistentWithAuthMe() {
        Session user = newUser("ctru");
        ResponseEntity<String> resp = meCall(user.accessToken());
        assertEquals(200, resp.getStatusCode().value());
        JsonNode data = dataNode(resp);
        assertRequiredPresent(data, "User", "GET /auth/me data");
        assertNoUndeclaredFields(data, "User", "GET /auth/me data");
    }

    // ---------- 2. Organization schema vs GET /organizations/{orgId} ----------

    @Test
    void organizationSchemaConsistentWithGetOrg() {
        Session owner = newUser("ctro");
        String orgId = createOrg(owner, unique("ctrorg")).path("id").asText();

        ResponseEntity<String> resp = rest.exchange("/api/v1/organizations/" + orgId, HttpMethod.GET,
                new HttpEntity<>(bearer(owner.accessToken())), String.class);
        assertEquals(200, resp.getStatusCode().value());
        JsonNode data = dataNode(resp);
        assertRequiredPresent(data, "Organization", "GET /organizations/{orgId} data");
        assertNoUndeclaredFields(data, "Organization", "GET /organizations/{orgId} data");
    }

    // ---------- 3. Member schema vs /organizations/{orgId}/members ----------

    @Test
    void memberSchemaConsistentWithOrgMembers() {
        Session owner = newUser("ctrmo");
        Session member = newUser("ctrmm");
        String orgId = createOrg(owner, unique("ctrm")).path("id").asText();
        assertEquals(201, addMember(owner, orgId, member.userPublicId(), "member").getStatusCode().value(),
                "添加成员失败");

        ResponseEntity<String> resp = rest.exchange("/api/v1/organizations/" + orgId + "/members",
                HttpMethod.GET, new HttpEntity<>(bearer(owner.accessToken())), String.class);
        assertEquals(200, resp.getStatusCode().value());
        JsonNode items = dataNode(resp).path("items");
        assertTrue(items.isArray() && !items.isEmpty(), "成员列表不应为空");

        JsonNode mine = null;
        for (JsonNode item : items) {
            if (member.userPublicId().equals(item.path("user").path("id").asText())) {
                mine = item;
                break;
            }
        }
        assertNotNull(mine, "新增成员未出现在成员列表中");

        // 契约 Member.required = [user, role, status, version]，user 为嵌套对象（$ref User）
        assertTrue(mine.path("user").isObject(), "Member.user 应为嵌套对象");
        assertTrue(!mine.path("user").path("id").asText().isBlank(), "Member.user.id 缺失");
        assertTrue(mine.hasNonNull("role"), "Member.role 缺失");
        assertTrue(mine.hasNonNull("status"), "Member.status 缺失");
        assertTrue(mine.hasNonNull("version"), "Member.version 缺失");
        assertTrue(enumOf("Member", "role").contains(mine.path("role").asText()),
                "role=" + mine.path("role").asText() + " 不在契约 Member.role 枚举");
        assertTrue(enumOf("Member", "status").contains(mine.path("status").asText()),
                "status=" + mine.path("status").asText() + " 不在契约 Member.status 枚举");
    }

    // ---------- 4. AccessRequest schema vs gated 申请 ----------

    @Test
    void accessRequestSchemaConsistentWithGatedFlow() throws Exception {
        Session owner = newUser("ctrao");
        Session applicant = newUser("ctrap");
        String ns = userNamespaceId(owner);
        String repoId = dataNode(createRepo(owner, ns, unique("CtrGate"), "public", true, null))
                .path("id").asText();
        awaitLifecycleHttp(owner.accessToken(), repoId, "active");

        ResponseEntity<String> resp = postJson(applicant.accessToken(),
                "/api/v1/repositories/" + repoId + "/access-requests",
                Map.of("reason", "契约一致性验证"), null);
        assertEquals(201, resp.getStatusCode().value());
        JsonNode data = dataNode(resp);
        assertRequiredPresent(data, "AccessRequest", "POST /access-requests data");
        assertNoUndeclaredFields(data, "AccessRequest", "POST /access-requests data");
        List<String> statusEnum = enumOf("AccessRequest", "status");
        assertTrue(statusEnum.contains(data.path("status").asText()),
                "status=" + data.path("status").asText() + " 不在契约枚举 " + statusEnum);
    }

    // ---------- 5. Job schema vs 删除仓库产生的异步 Job ----------

    @Test
    void jobSchemaConsistentWithDeletionJob() throws Exception {
        Session owner = newUser("ctrj");
        String ns = userNamespaceId(owner);
        String repoId = dataNode(createRepo(owner, ns, unique("CtrJob"), "public", null, null))
                .path("id").asText();
        JsonNode active = awaitLifecycleHttp(owner.accessToken(), repoId, "active");

        ResponseEntity<String> deleted = deleteRepo(owner.accessToken(), repoId,
                UUID.randomUUID().toString(), etagOfVersion(active.path("version").asLong()));
        assertEquals(202, deleted.getStatusCode().value());
        String jobId = dataNode(deleted).path("id").asText();

        ResponseEntity<String> resp = rest.exchange("/api/v1/jobs/" + jobId, HttpMethod.GET,
                new HttpEntity<>(bearer(owner.accessToken())), String.class);
        assertEquals(200, resp.getStatusCode().value());
        JsonNode job = dataNode(resp);
        assertRequiredPresent(job, "Job", "GET /jobs/{jobId} data");
        assertNoUndeclaredFields(job, "Job", "GET /jobs/{jobId} data");
        List<String> statusEnum = enumOf("Job", "status");
        assertTrue(statusEnum.contains(job.path("status").asText()),
                "status=" + job.path("status").asText() + " 不在契约枚举 " + statusEnum);
        // 可选字段非 null 时必须落在契约 Job.properties 声明范围内
        for (String optional : List.of("progressCurrent", "progressTotal", "progressMessage",
                "errorCode", "startedAt", "finishedAt", "resultSummary")) {
            if (job.path(optional).isMissingNode() || job.path(optional).isNull()) {
                continue;
            }
            assertTrue(propertiesOf("Job").contains(optional), "可选字段 " + optional + " 未在契约 Job 声明");
        }
    }

    // ---------- 6. 契约 paths 覆盖实现的关键端点 ----------

    @Test
    void contractCoversImplementedKeyPaths() {
        Map<String, Object> paths = paths();
        // servers.url = /api/v1：契约 paths 不带前缀，实现按 /api/v1/** 提供服务
        List<String> implemented = List.of(
                "/auth/register", "/auth/login", "/auth/me",
                "/organizations", "/organizations/{orgId}/members",
                "/repositories", "/repositories/{repoId}",
                "/repositories/{repoId}/files", "/repositories/{repoId}/uploads", "/uploads/{uploadId}",
                "/repositories/{repoId}/files/{fileId}/download-sessions",
                "/jobs/{jobId}", "/jobs/{jobId}:cancel", "/jobs/{jobId}/events",
                "/repositories/{repoId}/preview", "/repositories/{repoId}/preview-jobs",
                "/repositories/{repoId}/preview/download",
                "/api-keys", "/me/access-requests",
                "/admin/users/{userId}:unlock", "/admin/audit-logs",
                "/admin/users", "/admin/users/{userId}/roles",
                "/admin/roles", "/admin/permissions",
                "/admin/dicts", "/admin/dicts/{dictId}/items",
                "/admin/system/overview",
                "/me/menus",
                "/admin/menus", "/admin/menus/{code}",
                "/admin/menus/{code}:disable", "/admin/menus/{code}:enable");
        for (String path : implemented) {
            assertTrue(paths.containsKey(path), "契约 paths 缺少实现已提供的关键端点: " + path);
        }
    }

    // ---------- 7. ErrorResponse schema vs 404 响应 ----------

    @Test
    void errorEnvelopeConsistentWithErrorResponseSchema() {
        Session user = newUser("ctre");
        ResponseEntity<String> resp = repoDetail(user.accessToken(), UUID.randomUUID().toString());
        assertEquals(404, resp.getStatusCode().value());

        JsonNode body;
        try {
            body = JSON.readTree(resp.getBody());
        } catch (Exception e) {
            throw new IllegalStateException("404 响应体不是 JSON: " + resp.getBody(), e);
        }
        for (String field : requiredOf("ErrorResponse")) {
            assertTrue(body.hasNonNull(field), "ErrorResponse 必填字段缺失: " + field);
        }
        assertTrue(!body.path("message").asText().isBlank(), "ErrorResponse.message 不应为空白");
    }

    // ---------- 组织助手（与 OrganizationTests 同模式，SEQ 保证唯一） ----------

    private JsonNode createOrg(Session actor, String slug) {
        HttpHeaders headers = bearer(actor.accessToken());
        headers.setContentType(MediaType.APPLICATION_JSON);
        ResponseEntity<String> resp = rest.postForEntity("/api/v1/organizations",
                new HttpEntity<>(Map.of("slug", slug, "name", "契约测试-" + slug), headers), String.class);
        assertEquals(201, resp.getStatusCode().value(), "创建组织失败: " + resp.getBody());
        return dataNode(resp);
    }

    private ResponseEntity<String> addMember(Session actor, String orgId, String userPublicId, String role) {
        HttpHeaders headers = bearer(actor.accessToken());
        headers.setContentType(MediaType.APPLICATION_JSON);
        return rest.postForEntity("/api/v1/organizations/" + orgId + "/members",
                new HttpEntity<>(Map.of("userId", userPublicId, "role", role), headers), String.class);
    }
}
