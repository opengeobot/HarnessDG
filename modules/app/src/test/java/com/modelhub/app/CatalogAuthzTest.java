package com.modelhub.app;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;

import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * catalog 统一授权矩阵（02 §3-§5）：组织 visibility 读取语义、membership 派生角色、
 * user/organization 双形态协作者、防枚举 404/403 区分、协作者授权权限（grant 权限）。
 */
class CatalogAuthzTest extends CatalogTestSupport {

    @Test
    void organizationVisibilityAndCollaboratorUserSubject() throws Exception {
        Session a = newUser("azown");     // org1 owner
        Session b = newUser("azmemb");    // org1 member
        Session c = newUser("azview");    // org1 viewer
        Session d = newUser("azext");     // 外部用户
        Session e = newUser("azorg2");    // org2 owner

        String org1 = createOrg(a, unique("azo1").toLowerCase());
        assertEquals(201, addMember(a, org1, b.userPublicId(), "member").getStatusCode().value());
        assertEquals(201, addMember(a, org1, c.userPublicId(), "viewer").getStatusCode().value());
        String orgNs = orgNamespaceId(org1);

        String repoId = dataNode(createRepo(a, orgNs, unique("OrgVis"), "organization", null, null))
                .path("id").asText();
        awaitLifecycleHttp(a.accessToken(), repoId, "active");

        // 匿名 / 无关系用户 / 其他组织成员 → 404（防枚举）
        assertEquals(404, repoDetail(null, repoId).getStatusCode().value());
        assertEquals(404, repoDetail(d.accessToken(), repoId).getStatusCode().value());
        assertEquals(404, repoDetail(e.accessToken(), repoId).getStatusCode().value());

        // 有效成员（含 viewer）可读；owner 可读
        assertEquals(200, repoDetail(b.accessToken(), repoId).getStatusCode().value());
        assertEquals(200, repoDetail(c.accessToken(), repoId).getStatusCode().value());
        assertEquals(200, repoDetail(a.accessToken(), repoId).getStatusCode().value());

        // member 未自建仓库 → 无 WRITE，PATCH → 403（有关系不 404）
        long version = dataNode(repoDetail(b.accessToken(), repoId)).path("version").asLong();
        assertEquals(403, patchRepo(b.accessToken(), repoId,
                Map.of("displayName", "X"), etagOfVersion(version)).getStatusCode().value());

        // viewer 无权在组织 ns 创建仓库 → 403
        assertEquals(403, createRepo(c, orgNs, unique("Nope"), "organization", null, null)
                .getStatusCode().value());

        // 协作者列表仅 ADMIN：member → 403，owner → 200
        String collabPath = "/api/v1/repositories/" + repoId + "/collaborators";
        assertEquals(403, rest.exchange(collabPath, HttpMethod.GET,
                new HttpEntity<>(bearer(b.accessToken())), String.class).getStatusCode().value());
        assertEquals(200, rest.exchange(collabPath, HttpMethod.GET,
                new HttpEntity<>(bearer(a.accessToken())), String.class).getStatusCode().value());

        // A 授 D write：D 可读可写
        assertEquals(201, addCollaborator(a, repoId, "user", d.userPublicId(), "write")
                .getStatusCode().value());
        assertEquals(200, repoDetail(d.accessToken(), repoId).getStatusCode().value());
        long dVersion = dataNode(repoDetail(d.accessToken(), repoId)).path("version").asLong();
        assertEquals(200, patchRepo(d.accessToken(), repoId,
                Map.of("displayName", "ByCollab"), etagOfVersion(dVersion)).getStatusCode().value());

        // 移除 D 后回到 404（user subject 关系消失）
        assertEquals(204, removeCollaborator(a, repoId, d.userPublicId()).getStatusCode().value());
        assertEquals(404, repoDetail(d.accessToken(), repoId).getStatusCode().value());

        // D 持 maintain 后可授 write/read，不能授 admin（02 §3.3 checkGrantPermission）
        assertEquals(201, addCollaborator(a, repoId, "user", d.userPublicId(), "maintain")
                .getStatusCode().value());
        assertEquals(403, addCollaborator(d, repoId, "user", e.userPublicId(), "admin")
                .getStatusCode().value());
        assertEquals(201, addCollaborator(d, repoId, "user", e.userPublicId(), "write")
                .getStatusCode().value());
        assertEquals(200, repoDetail(e.accessToken(), repoId).getStatusCode().value());
    }

    @Test
    void organizationSubjectCollaboratorInheritsToMembers() throws Exception {
        Session a = newUser("azpriv");    // 个人私有仓库 owner
        Session e = newUser("azo2own");   // org2 owner
        Session f = newUser("azo2mem");   // org2 member
        Session d = newUser("azo2ext");   // 无关用户

        String org2 = createOrg(e, unique("azo2").toLowerCase());
        assertEquals(201, addMember(e, org2, f.userPublicId(), "member").getStatusCode().value());

        String ns = userNamespaceId(a);
        String repoId = dataNode(createRepo(a, ns, unique("Priv"), "private", null, null))
                .path("id").asText();
        awaitLifecycleHttp(a.accessToken(), repoId, "active");

        // 未授权前组织成员不可见
        assertEquals(404, repoDetail(f.accessToken(), repoId).getStatusCode().value());

        // 以 organization 形态授 org2 read → 全体有效成员继承 READ
        assertEquals(201, addCollaborator(a, repoId, "organization", org2, "read")
                .getStatusCode().value());
        assertEquals(200, repoDetail(e.accessToken(), repoId).getStatusCode().value());
        assertEquals(200, repoDetail(f.accessToken(), repoId).getStatusCode().value());
        assertEquals(404, repoDetail(d.accessToken(), repoId).getStatusCode().value());

        // READ 不含 WRITE：PATCH 拒绝（user 维度无关系 → 防枚举 404）
        long version = dataNode(repoDetail(f.accessToken(), repoId)).path("version").asLong();
        int patchStatus = patchRepo(f.accessToken(), repoId,
                Map.of("displayName", "X"), etagOfVersion(version)).getStatusCode().value();
        assertTrue(patchStatus == 403 || patchStatus == 404,
                "READ 协作者不应可写，实际=" + patchStatus);
    }

    // ---------- helpers ----------

    private String createOrg(Session actor, String slug) {
        HttpHeaders h = bearer(actor.accessToken());
        h.setContentType(MediaType.APPLICATION_JSON);
        ResponseEntity<String> resp = rest.postForEntity("/api/v1/organizations",
                new HttpEntity<>(Map.of("slug", slug, "name", "组织 " + slug), h), String.class);
        if (resp.getStatusCode().value() != 201) {
            throw new IllegalStateException("create org failed: " + resp.getBody());
        }
        return dataNode(resp).path("id").asText();
    }

    private ResponseEntity<String> addMember(Session actor, String orgId, String userPublicId, String role) {
        HttpHeaders h = bearer(actor.accessToken());
        h.setContentType(MediaType.APPLICATION_JSON);
        return rest.postForEntity("/api/v1/organizations/" + orgId + "/members",
                new HttpEntity<>(Map.of("userId", userPublicId, "role", role), h), String.class);
    }

    /** 组织 namespace 的 publicId（经内部 id 查询）。 */
    private String orgNamespaceId(String orgPublicId) {
        Long internalId = organizations.findByPublicId(UUID.fromString(orgPublicId)).orElseThrow().getId();
        return namespaces.findByOrganizationIdAndNamespaceType(internalId, "organization")
                .orElseThrow().getPublicId().toString();
    }

    private ResponseEntity<String> addCollaborator(Session actor, String repoId,
                                                   String subjectType, String subjectId, String role) {
        HttpHeaders h = bearer(actor.accessToken());
        h.setContentType(MediaType.APPLICATION_JSON);
        return rest.postForEntity("/api/v1/repositories/" + repoId + "/collaborators",
                new HttpEntity<>(Map.of("subjectId", subjectId, "subjectType", subjectType, "role", role),
                        h), String.class);
    }

    private ResponseEntity<String> removeCollaborator(Session actor, String repoId, String subjectId) {
        return rest.exchange("/api/v1/repositories/" + repoId + "/collaborators/" + subjectId,
                HttpMethod.DELETE, new HttpEntity<>(bearer(actor.accessToken())), String.class);
    }
}
