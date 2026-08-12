package com.modelhub.app;

import com.fasterxml.jackson.databind.JsonNode;
import com.modelhub.shared.web.ETags;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 组织与成员（02 §3.2 / §8 权限矩阵）：
 * owner/admin/member/viewer 数据驱动矩阵、仅 owner 授予/撤销 owner、
 * 最后一个 active owner 保护、If-Match 条件更新、cursor 成员分页、platform_admin 越级。
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class OrganizationTests extends BaseIntegrationTest {

    private Session owner;
    private Session admin;
    private Session member;
    private Session viewer;
    private Session outsider;
    private Session platformAdmin;
    private String orgId;

    @BeforeAll
    void setupOrganization() throws Exception {
        owner = register(unique("orgo"), "Passw0rd-x");
        admin = register(unique("orga"), "Passw0rd-x");
        member = register(unique("orgm"), "Passw0rd-x");
        viewer = register(unique("orgv"), "Passw0rd-x");
        outsider = register(unique("orgx"), "Passw0rd-x");
        platformAdmin = login("platform-root", "Boot-Strap-1x");

        orgId = createOrg(owner, unique("team")).path("publicId").asText();
        assertThat(addMember(owner, orgId, admin.userPublicId(), "admin").getStatusCode().value()).isEqualTo(201);
        assertThat(addMember(owner, orgId, member.userPublicId(), "member").getStatusCode().value()).isEqualTo(201);
        assertThat(addMember(owner, orgId, viewer.userPublicId(), "viewer").getStatusCode().value()).isEqualTo(201);
    }

    /** 02 §8 权限矩阵：组织级操作的 角色 × 期望状态。 */
    static Stream<Arguments> orgPermissionMatrix() {
        return Stream.of(
                Arguments.of("owner", "GET_ORG", 200),
                Arguments.of("admin", "GET_ORG", 200),
                Arguments.of("member", "GET_ORG", 200),
                Arguments.of("viewer", "GET_ORG", 200),
                Arguments.of("outsider", "GET_ORG", 404),
                Arguments.of("owner", "UPDATE_ORG", 200),
                Arguments.of("admin", "UPDATE_ORG", 200),
                Arguments.of("member", "UPDATE_ORG", 403),
                Arguments.of("viewer", "UPDATE_ORG", 403),
                Arguments.of("outsider", "UPDATE_ORG", 404),
                Arguments.of("owner", "LIST_MEMBERS", 200),
                Arguments.of("admin", "LIST_MEMBERS", 200),
                Arguments.of("member", "LIST_MEMBERS", 200),
                Arguments.of("viewer", "LIST_MEMBERS", 200),
                Arguments.of("outsider", "LIST_MEMBERS", 404),
                Arguments.of("owner", "ADD_MEMBER", 201),
                Arguments.of("admin", "ADD_MEMBER", 201),
                Arguments.of("member", "ADD_MEMBER", 403),
                Arguments.of("viewer", "ADD_MEMBER", 403),
                Arguments.of("owner", "ADD_OWNER", 201),
                Arguments.of("admin", "ADD_OWNER", 403));
    }

    @ParameterizedTest(name = "{0} -> {1} => {2}")
    @MethodSource("orgPermissionMatrix")
    void permission_matrix(String role, String action, int expected) {
        Session actor = switch (role) {
            case "owner" -> owner;
            case "admin" -> admin;
            case "member" -> member;
            case "viewer" -> viewer;
            default -> outsider;
        };
        int status = execute(actor, action);
        assertThat(status).as("%s -> %s", role, action).isEqualTo(expected);
    }

    private int execute(Session actor, String action) {
        return switch (action) {
            case "GET_ORG" -> orgCall(actor, HttpMethod.GET, null, null).getStatusCode().value();
            case "UPDATE_ORG" -> {
                String etag = currentEtag(actor);
                if (etag == null) {
                    yield orgCall(actor, HttpMethod.PATCH, Map.of("name", "更新名"), null).getStatusCode().value();
                }
                yield orgCall(actor, HttpMethod.PATCH, Map.of("name", "更新名-" + System.nanoTime()), etag)
                        .getStatusCode().value();
            }
            case "LIST_MEMBERS" -> rest.exchange("/api/v1/organizations/" + orgId + "/members", HttpMethod.GET,
                    new HttpEntity<>(bearer(actor.accessToken())), String.class).getStatusCode().value();
            case "ADD_MEMBER" -> addMember(actor, orgId, register(unique("tmp"), "Passw0rd-x").userPublicId(),
                    "member").getStatusCode().value();
            case "ADD_OWNER" -> addMember(actor, orgId, register(unique("tmpo"), "Passw0rd-x").userPublicId(),
                    "owner").getStatusCode().value();
            default -> throw new IllegalArgumentException(action);
        };
    }

    @Test
    void create_org_duplicate_slug_conflicts() {
        String slug = unique("dup");
        createOrg(owner, slug);
        HttpHeaders headers = bearer(owner.accessToken());
        headers.setContentType(MediaType.APPLICATION_JSON);
        ResponseEntity<String> dup = rest.postForEntity("/api/v1/organizations",
                new HttpEntity<>(Map.of("slug", slug, "name", "重名组织"), headers), String.class);
        assertThat(dup.getStatusCode().value()).isEqualTo(409);
    }

    @Test
    void create_org_returns_namespace_id() {
        JsonNode org = createOrg(owner, unique("nsx"));
        assertThat(org.path("namespaceId").asText()).isNotBlank();
        assertThat(org.path("etag").asText()).isNotBlank();
    }

    @Test
    void update_org_requires_if_match() {
        // 缺 If-Match → 400
        int missing = orgCall(owner, HttpMethod.PATCH, Map.of("name", "x"), null).getStatusCode().value();
        assertThat(missing).isEqualTo(400);
        // 错误 ETag → 412
        HttpHeaders bad = bearer(owner.accessToken());
        bad.setContentType(MediaType.APPLICATION_JSON);
        bad.setIfMatch("\"badbad\"");
        ResponseEntity<String> conflict = rest.exchange("/api/v1/organizations/" + orgId, HttpMethod.PATCH,
                new HttpEntity<>(Map.of("name", "x"), bad), String.class);
        assertThat(conflict.getStatusCode().value()).isEqualTo(412);
    }

    @Test
    void only_owner_grants_or_revokes_owner_role() {
        String tempId = register(unique("promo"), "Passw0rd-x").userPublicId();
        // owner 先加为 admin
        assertThat(addMember(owner, orgId, tempId, "admin").getStatusCode().value()).isEqualTo(201);

        // admin 不能把 member 提升为 owner
        HttpHeaders headers = bearer(admin.accessToken());
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setIfMatch(ETags.ofVersion(1)); // member rank=1
        ResponseEntity<String> promote = rest.exchange(
                "/api/v1/organizations/" + orgId + "/members/" + member.userPublicId(), HttpMethod.PATCH,
                new HttpEntity<>(Map.of("role", "owner"), headers), String.class);
        assertThat(promote.getStatusCode().value()).isEqualTo(403);

        // admin 不能移除 owner
        ResponseEntity<String> remove = rest.exchange(
                "/api/v1/organizations/" + orgId + "/members/" + owner.userPublicId(), HttpMethod.DELETE,
                new HttpEntity<>(bearer(admin.accessToken())), String.class);
        assertThat(remove.getStatusCode().value()).isEqualTo(403);
    }

    @Test
    void last_active_owner_cannot_be_demoted_or_removed() {
        Session soloOwner = register(unique("solo2"), "Passw0rd-x");
        String orgSolo = createOrg(soloOwner, unique("soloorg2")).path("publicId").asText();

        // 降级最后 owner → 409
        HttpHeaders demote = bearer(soloOwner.accessToken());
        demote.setContentType(MediaType.APPLICATION_JSON);
        demote.setIfMatch(ETags.ofVersion(3)); // owner rank=3
        ResponseEntity<String> demoted = rest.exchange(
                "/api/v1/organizations/" + orgSolo + "/members/" + soloOwner.userPublicId(), HttpMethod.PATCH,
                new HttpEntity<>(Map.of("role", "member"), demote), String.class);
        assertThat(demoted.getStatusCode().value()).isEqualTo(409);

        // 移除最后 owner → 409
        ResponseEntity<String> removed = rest.exchange(
                "/api/v1/organizations/" + orgSolo + "/members/" + soloOwner.userPublicId(), HttpMethod.DELETE,
                new HttpEntity<>(bearer(soloOwner.accessToken())), String.class);
        assertThat(removed.getStatusCode().value()).isEqualTo(409);
    }

    @Test
    void member_role_update_uses_role_rank_etag() {
        String tempId = register(unique("etag"), "Passw0rd-x").userPublicId();
        assertThat(addMember(owner, orgId, tempId, "viewer").getStatusCode().value()).isEqualTo(201);

        HttpHeaders wrong = bearer(owner.accessToken());
        wrong.setContentType(MediaType.APPLICATION_JSON);
        wrong.setIfMatch(ETags.ofVersion(1)); // 实际是 viewer(rank 0)
        ResponseEntity<String> mismatch = rest.exchange(
                "/api/v1/organizations/" + orgId + "/members/" + tempId, HttpMethod.PATCH,
                new HttpEntity<>(Map.of("role", "member"), wrong), String.class);
        assertThat(mismatch.getStatusCode().value()).isEqualTo(412);

        HttpHeaders ok = bearer(owner.accessToken());
        ok.setContentType(MediaType.APPLICATION_JSON);
        ok.setIfMatch(ETags.ofVersion(0));
        ResponseEntity<String> updated = rest.exchange(
                "/api/v1/organizations/" + orgId + "/members/" + tempId, HttpMethod.PATCH,
                new HttpEntity<>(Map.of("role", "member"), ok), String.class);
        assertThat(updated.getStatusCode().value()).isEqualTo(200);
    }

    @Test
    void removed_member_can_be_reactivated() {
        Session temp = register(unique("react"), "Passw0rd-x");
        String tempId = temp.userPublicId();
        assertThat(addMember(owner, orgId, tempId, "viewer").getStatusCode().value()).isEqualTo(201);
        ResponseEntity<String> remove = rest.exchange(
                "/api/v1/organizations/" + orgId + "/members/" + tempId, HttpMethod.DELETE,
                new HttpEntity<>(bearer(owner.accessToken())), String.class);
        assertThat(remove.getStatusCode().value()).isEqualTo(204);
        // 被移除后不在 active 成员列表
        assertThat(listMemberUsernames(owner)).doesNotContain(temp.username());
        // 重新加入 → 201（复用原 membership 行，避开唯一约束冲突）
        assertThat(addMember(owner, orgId, tempId, "member").getStatusCode().value()).isEqualTo(201);
    }

    @Test
    void platform_admin_can_access_any_organization() {
        ResponseEntity<String> resp = orgCall(platformAdmin, HttpMethod.GET, null, null);
        assertThat(resp.getStatusCode().value()).isEqualTo(200);
    }

    @Test
    void member_cursor_pagination_is_complete_and_distinct() throws Exception {
        Session pagOwner = register(unique("pago"), "Passw0rd-x");
        String pagOrg = createOrg(pagOwner, unique("pagorg")).path("publicId").asText();
        Set<String> expected = new HashSet<>();
        expected.add(pagOwner.username());
        for (int i = 0; i < 5; i++) {
            Session u = register(unique("pagu"), "Passw0rd-x");
            expected.add(u.username());
            assertThat(addMember(pagOwner, pagOrg, u.userPublicId(), "member").getStatusCode().value())
                    .isEqualTo(201);
        }

        List<String> seen = new ArrayList<>();
        String cursor = null;
        int pages = 0;
        do {
            String uri = "/api/v1/organizations/" + pagOrg + "/members?limit=2"
                    + (cursor == null ? "" : "&cursor=" + cursor);
            ResponseEntity<String> resp = rest.exchange(uri, HttpMethod.GET,
                    new HttpEntity<>(bearer(pagOwner.accessToken())), String.class);
            assertThat(resp.getStatusCode().value()).isEqualTo(200);
            JsonNode data = JSON.readTree(resp.getBody()).path("data");
            data.path("items").forEach(n -> seen.add(n.path("username").asText()));
            cursor = data.path("nextCursor").isNull() ? null : data.path("nextCursor").asText();
            assertThat(++pages).isLessThan(10);
        } while (cursor != null);

        assertThat(seen).hasSize(6);
        assertThat(new HashSet<>(seen)).isEqualTo(expected);
    }

    // ---------- helpers ----------

    private JsonNode createOrg(Session actor, String slug) {
        HttpHeaders headers = bearer(actor.accessToken());
        headers.setContentType(MediaType.APPLICATION_JSON);
        ResponseEntity<String> resp = rest.postForEntity("/api/v1/organizations",
                new HttpEntity<>(Map.of("slug", slug, "name", "组织 " + slug), headers), String.class);
        if (resp.getStatusCode().value() != 201) {
            throw new IllegalStateException("create org failed: " + resp.getBody());
        }
        return json(resp).path("data");
    }

    private ResponseEntity<String> addMember(Session actor, String orgId, String userPublicId, String role) {
        HttpHeaders headers = bearer(actor.accessToken());
        headers.setContentType(MediaType.APPLICATION_JSON);
        return rest.postForEntity("/api/v1/organizations/" + orgId + "/members",
                new HttpEntity<>(Map.of("userId", userPublicId, "role", role), headers), String.class);
    }

    private ResponseEntity<String> orgCall(Session actor, HttpMethod method, Map<String, Object> body,
                                           String ifMatch) {
        HttpHeaders headers = bearer(actor.accessToken());
        if (body != null) {
            headers.setContentType(MediaType.APPLICATION_JSON);
        }
        if (ifMatch != null) {
            headers.setIfMatch(ifMatch);
        }
        return rest.exchange("/api/v1/organizations/" + orgId, method, new HttpEntity<>(body, headers),
                String.class);
    }

    private String currentEtag(Session actor) {
        ResponseEntity<String> resp = orgCall(actor, HttpMethod.GET, null, null);
        if (!resp.getStatusCode().is2xxSuccessful()) {
            return null;
        }
        return json(resp).path("data").path("etag").asText();
    }

    private List<String> listMemberUsernames(Session actor) {
        ResponseEntity<String> resp = rest.exchange("/api/v1/organizations/" + orgId + "/members",
                HttpMethod.GET, new HttpEntity<>(bearer(actor.accessToken())), String.class);
        List<String> names = new ArrayList<>();
        json(resp).path("data").path("items").forEach(n -> names.add(n.path("username").asText()));
        return names;
    }

    private static JsonNode json(ResponseEntity<String> resp) {
        try {
            return JSON.readTree(resp.getBody());
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }
}
