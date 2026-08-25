package com.modelhub.app;

import com.fasterxml.jackson.databind.JsonNode;
import com.modelhub.identity.domain.SysRoleEntity;
import com.modelhub.identity.domain.SysUserRoleEntity;
import com.modelhub.identity.domain.UserEntity;
import com.modelhub.identity.repo.SysRoleRepository;
import com.modelhub.identity.repo.SysUserRoleRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;

import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 管理后台集成测试（管理后台计划 §九）：
 * RBAC 迁移兼容（bootstrap platform-root 经 V16 后登录角色正确）、用户列表/禁用/启用闭环、
 * 角色与指派管理（守卫规则 + 吊销即时生效）、通用字典管理、系统概览、审计 from/to 过滤。
 * 角色指派经注入 SysUserRoleRepository 直接落库（模拟已有指派，避免依赖待测端点自举）。
 */
class AdminConsoleTests extends CatalogTestSupport {

    @Autowired
    private SysRoleRepository roleRepo;

    @Autowired
    private SysUserRoleRepository userRoleRepo;

    // ---------- 1. RBAC 迁移兼容 ----------

    @Test
    void bootstrapRootRolesResolvedFromRbacTables() {
        Session root = login("platform-root", "Boot-Strap-1x");
        JsonNode me = dataNode(meCall(root.accessToken()));
        assertEquals("platform-root", me.path("username").asText());
        JsonNode roles = me.path("platformRoles");
        assertTrue(roles.isArray(), "platformRoles 应为数组");
        boolean hasAdmin = false;
        for (JsonNode r : roles) {
            if ("platform_admin".equals(r.asText())) {
                hasAdmin = true;
            }
        }
        assertTrue(hasAdmin, "bootstrap platform-root 应持有 platform_admin（V16 迁移后）: " + roles);
    }

    // ---------- 2. 用户列表 ----------

    @Test
    void userListFilterPaginationAndAccessControl() {
        Session root = login("platform-root", "Boot-Strap-1x");
        Session plain = newUser("acplain");
        Session auditor = assignRoleDirect(newUser("acauditor"), "platform_auditor");

        // q 过滤 + 命中新用户
        ResponseEntity<String> resp = adminGet(root.accessToken(), "/api/v1/admin/users?q=acplain");
        assertEquals(200, resp.getStatusCode().value());
        JsonNode items = dataNode(resp).path("items");
        assertTrue(items.size() >= 1, "q 过滤应命中: " + items);
        for (JsonNode it : items) {
            assertTrue(it.path("username").asText().contains("acplain"));
            assertTrue(it.hasNonNull("status") && it.hasNonNull("createdAt"));
        }

        // 分页：limit=2 有 nextCursor，续页不重叠
        ResponseEntity<String> page1 = adminGet(root.accessToken(), "/api/v1/admin/users?limit=2");
        assertEquals(200, page1.getStatusCode().value());
        assertEquals(2, dataNode(page1).path("items").size());
        String cursor = dataNode(page1).path("nextCursor").asText();
        assertFalse(cursor.isBlank(), "用户总数 > 2 时应返回 nextCursor");
        ResponseEntity<String> page2 = adminGet(root.accessToken(), "/api/v1/admin/users?limit=2&cursor=" + cursor);
        assertEquals(200, page2.getStatusCode().value());
        assertTrue(dataNode(page2).path("items").size() >= 1);
        String firstOfPage2 = dataNode(page2).path("items").get(0).path("username").asText();
        for (JsonNode it : dataNode(page1).path("items")) {
            assertFalse(firstOfPage2.equals(it.path("username").asText()), "续页与首页不应重叠");
        }

        // status 过滤
        ResponseEntity<String> byStatus = adminGet(root.accessToken(), "/api/v1/admin/users?status=disabled");
        assertEquals(200, byStatus.getStatusCode().value());
        for (JsonNode it : dataNode(byStatus).path("items")) {
            assertEquals("disabled", it.path("status").asText());
        }

        // 访问控制：普通用户 403；auditor 无 admin:user:manage 语义，同样 403
        assertEquals(403, adminGet(plain.accessToken(), "/api/v1/admin/users").getStatusCode().value());
        assertEquals(403, adminGet(auditor.accessToken(), "/api/v1/admin/users").getStatusCode().value());
        assertEquals("FORBIDDEN", errorCode(adminGet(auditor.accessToken(), "/api/v1/admin/users")));
        assertEquals(401, adminGet(null, "/api/v1/admin/users").getStatusCode().value());
    }

    // ---------- 3. 禁用/启用 ----------

    @Test
    void disableEnableCycleWithSessionInvalidationAndAudit() {
        Session root = login("platform-root", "Boot-Strap-1x");
        Session victim = newUser("acvictim");
        String idem = UUID.randomUUID().toString();

        ResponseEntity<String> disabled = adminPost(root.accessToken(),
                "/api/v1/admin/users/" + victim.userPublicId() + ":disable", null, idem);
        assertEquals(200, disabled.getStatusCode().value(), "禁用应成功: " + disabled.getBody());
        assertEquals("disabled", dataNode(disabled).path("status").asText());
        assertEquals(idem, disabled.getHeaders().getFirst("Idempotency-Key"));

        // 存量 JWT 因 auth_version 递增立即失效
        ResponseEntity<String> me = meCall(victim.accessToken());
        assertEquals(401, me.getStatusCode().value(), "被禁用用户的既有会话应 401");

        // 重复禁用 409
        assertEquals(409, adminPost(root.accessToken(),
                "/api/v1/admin/users/" + victim.userPublicId() + ":disable", null, idem).getStatusCode().value());

        // 启用恢复：重新登录成功
        ResponseEntity<String> enabled = adminPost(root.accessToken(),
                "/api/v1/admin/users/" + victim.userPublicId() + ":enable", null, idem);
        assertEquals(200, enabled.getStatusCode().value());
        assertEquals("active", dataNode(enabled).path("status").asText());
        Session again = login(victim.username(), "Passw0rd-9x1");
        assertEquals(200, meCall(again.accessToken()).getStatusCode().value());

        // 审计闭环
        JsonNode audits = dataNode(adminGet(root.accessToken(),
                "/api/v1/admin/audit-logs?action=user.admin_disable&actor=" + root.username())).path("items");
        boolean found = false;
        for (JsonNode a : audits) {
            if (a.path("resource").asText().equals("user:" + victim.userPublicId())) {
                found = true;
                assertEquals("success", a.path("result").asText());
            }
        }
        assertTrue(found, "user.admin_disable 审计应落库: " + audits);
    }

    @Test
    void lastPlatformAdminGuards() {
        Session root = login("platform-root", "Boot-Strap-1x");
        String idem = UUID.randomUUID().toString();
        // 共享库隔离：先吊销 root 之外的全部活跃 platform_admin 指派（其他用例可能新增），
        // 恢复「最后一个管理员」前提后再验证守卫。
        revokeAdminsExcept(root);

        // 禁用最后一个 platform_admin 账户 → 409
        ResponseEntity<String> disable = adminPost(root.accessToken(),
                "/api/v1/admin/users/" + root.userPublicId() + ":disable", null, idem);
        assertEquals(409, disable.getStatusCode().value());
        assertEquals("CONFLICT", errorCode(disable));

        // 吊销最后一个 platform_admin 的角色 → 409
        ResponseEntity<String> revoke = adminPost(root.accessToken(),
                "/api/v1/admin/users/" + root.userPublicId() + "/roles/platform_admin:revoke", null, idem);
        assertEquals(409, revoke.getStatusCode().value());
        assertEquals("CONFLICT", errorCode(revoke));
    }

    // ---------- 4. 角色与指派 ----------

    @Test
    void roleLifecycleAssignRevokeAndGuards() {
        Session root = login("platform-root", "Boot-Strap-1x");
        String idem = UUID.randomUUID().toString();

        // 创建自定义角色（勾选权限点）
        String code = unique("ops_role_").toLowerCase();
        ResponseEntity<String> created = adminPost(root.accessToken(), "/api/v1/admin/roles",
                Map.of("code", code, "description", "运营角色",
                        "permissions", java.util.List.of("admin:user:view", "admin:dict:view")), idem);
        assertEquals(201, created.getStatusCode().value(), "创建角色失败: " + created.getBody());
        JsonNode role = dataNode(created);
        assertEquals(code, role.path("code").asText());
        assertFalse(role.path("builtIn").asBoolean());
        assertEquals(2, role.path("permissions").size());
        String roleId = role.path("id").asText();

        // code 重复 409；未知权限码 400
        assertEquals(409, adminPost(root.accessToken(), "/api/v1/admin/roles",
                Map.of("code", code), idem).getStatusCode().value());
        assertEquals(400, adminPost(root.accessToken(), "/api/v1/admin/roles",
                Map.of("code", unique("bad_role_").toLowerCase(),
                        "permissions", java.util.List.of("not:a:perm")), idem).getStatusCode().value());

        // 权限点列表含内置管理面词表
        JsonNode perms = dataNode(adminGet(root.accessToken(), "/api/v1/admin/permissions")).path("items");
        boolean hasUserView = false;
        for (JsonNode p : perms) {
            if ("admin:user:view".equals(p.path("code").asText())) {
                hasUserView = true;
            }
        }
        assertTrue(hasUserView, "权限点列表应含内置权限: " + perms);

        // 指派给新用户：/auth/me 实时反映（角色每请求解析）
        Session member = newUser("acmember");
        ResponseEntity<String> assigned = adminPost(root.accessToken(),
                "/api/v1/admin/users/" + member.userPublicId() + "/roles",
                Map.of("roleCode", code), idem);
        assertEquals(201, assigned.getStatusCode().value(), "指派失败: " + assigned.getBody());
        JsonNode me = dataNode(meCall(member.accessToken()));
        boolean memberHasRole = false;
        for (JsonNode r : me.path("platformRoles")) {
            if (code.equals(r.asText())) {
                memberHasRole = true;
            }
        }
        assertTrue(memberHasRole, "指派后 /auth/me 应实时含新角色: " + me.path("platformRoles"));

        // 重复指派 409；自我指派 403
        assertEquals(409, adminPost(root.accessToken(),
                "/api/v1/admin/users/" + member.userPublicId() + "/roles",
                Map.of("roleCode", code), idem).getStatusCode().value());
        assertEquals(403, adminPost(root.accessToken(),
                "/api/v1/admin/users/" + root.userPublicId() + "/roles",
                Map.of("roleCode", code), idem).getStatusCode().value());

        // 内置角色禁改权限/禁删（409）
        JsonNode builtIn = findRole(root, "platform_admin");
        String builtInId = builtIn.path("id").asText();
        long builtInVersion = builtIn.path("version").asLong();
        ResponseEntity<String> touchBuiltIn = adminPatch(root.accessToken(),
                "/api/v1/admin/roles/" + builtInId,
                Map.of("permissions", java.util.List.of("admin:dict:view")),
                etagOfVersion(builtInVersion), idem);
        assertEquals(409, touchBuiltIn.getStatusCode().value());
        assertEquals(409, adminDelete(root.accessToken(),
                "/api/v1/admin/roles/" + builtInId, idem).getStatusCode().value());

        // 自我吊销 403：member 持有自建角色后吊销自己 → 403（无 admin 权限亦先被 403 拦截，
        // 此处用 root 吊销他人作为正常路径，另以第二个管理员验证自我吊销守卫）
        Session admin2 = assignRoleDirect(newUser("acadmin2"), "platform_admin");
        assertEquals(403, adminPost(admin2.accessToken(),
                "/api/v1/admin/users/" + admin2.userPublicId() + "/roles/platform_admin:revoke",
                null, idem).getStatusCode().value());

        // 吊销 member 的自建角色：即时生效（下一次请求即无该角色）
        ResponseEntity<String> revoked = adminPost(root.accessToken(),
                "/api/v1/admin/users/" + member.userPublicId() + "/roles/" + code + ":revoke", null, idem);
        assertEquals(200, revoked.getStatusCode().value(), "吊销失败: " + revoked.getBody());
        JsonNode meAfter = dataNode(meCall(member.accessToken()));
        for (JsonNode r : meAfter.path("platformRoles")) {
            assertFalse(code.equals(r.asText()), "吊销后角色应即时失效");
        }

        // 无活跃指派后可删除；删除后列表不再含该角色
        assertEquals(204, adminDelete(root.accessToken(),
                "/api/v1/admin/roles/" + roleId, idem).getStatusCode().value());
        JsonNode roleList = dataNode(adminGet(root.accessToken(), "/api/v1/admin/roles")).path("items");
        for (JsonNode r : roleList) {
            assertFalse(code.equals(r.path("code").asText()), "删除后角色列表不应含: " + code);
        }

        // 审计闭环
        JsonNode audits = dataNode(adminGet(root.accessToken(),
                "/api/v1/admin/audit-logs?action=role.create")).path("items");
        boolean auditFound = false;
        for (JsonNode a : audits) {
            if (a.path("resource").asText().equals("role:" + code)) {
                auditFound = true;
            }
        }
        assertTrue(auditFound, "role.create 审计应落库: " + audits);
    }

    // ---------- 5. 通用字典 ----------

    @Test
    void dictAndItemManagement() {
        Session root = login("platform-root", "Boot-Strap-1x");
        String idem = UUID.randomUUID().toString();

        // V17 种子字典可见
        JsonNode dictList = dataNode(adminGet(root.accessToken(), "/api/v1/admin/dicts")).path("items");
        boolean seedFound = false;
        for (JsonNode d : dictList) {
            if ("sys_user_status".equals(d.path("dictCode").asText())) {
                seedFound = true;
                assertTrue(d.path("itemCount").asLong() >= 3, "种子字典应含三项: " + d);
            }
        }
        assertTrue(seedFound, "应含种子字典 sys_user_status: " + dictList);

        // 创建字典；dict_code 重复 409
        String dictCode = unique("ac_dict_").toLowerCase();
        ResponseEntity<String> created = adminPost(root.accessToken(), "/api/v1/admin/dicts",
                Map.of("dictCode", dictCode, "name", "测试字典", "description", "集成测试"), idem);
        assertEquals(201, created.getStatusCode().value(), "创建字典失败: " + created.getBody());
        String dictId = dataNode(created).path("id").asText();
        assertEquals(409, adminPost(root.accessToken(), "/api/v1/admin/dicts",
                Map.of("dictCode", dictCode, "name", "重复"), idem).getStatusCode().value());

        // 建项：中英标签；项值重复 409
        ResponseEntity<String> item1 = adminPost(root.accessToken(), "/api/v1/admin/dicts/" + dictId + "/items",
                Map.of("itemValue", "low", "labelZh", "低", "labelEn", "Low", "sortOrder", 1), idem);
        assertEquals(201, item1.getStatusCode().value(), "创建字典项失败: " + item1.getBody());
        assertEquals("低", dataNode(item1).path("labelZh").asText());
        long itemId = dataNode(item1).path("id").asLong();
        assertEquals(409, adminPost(root.accessToken(), "/api/v1/admin/dicts/" + dictId + "/items",
                Map.of("itemValue", "low", "labelZh", "低", "labelEn", "Low"), idem).getStatusCode().value());

        // 更新项标签与排序（item_value 不可改，请求体无该字段）
        ResponseEntity<String> patched = adminPatch(root.accessToken(),
                "/api/v1/admin/dicts/" + dictId + "/items/" + itemId,
                Map.of("labelEn", "Low Priority", "sortOrder", 9), null, idem);
        assertEquals(200, patched.getStatusCode().value());
        assertEquals("Low Priority", dataNode(patched).path("labelEn").asText());
        assertEquals("low", dataNode(patched).path("itemValue").asText());

        // 停用/启用；重复停用 409
        ResponseEntity<String> off = adminPost(root.accessToken(),
                "/api/v1/admin/dicts/" + dictId + "/items/" + itemId + ":disable", null, idem);
        assertEquals(200, off.getStatusCode().value());
        assertEquals("disabled", dataNode(off).path("status").asText());
        assertEquals(409, adminPost(root.accessToken(),
                "/api/v1/admin/dicts/" + dictId + "/items/" + itemId + ":disable", null, idem).getStatusCode().value());
        assertEquals(200, adminPost(root.accessToken(),
                "/api/v1/admin/dicts/" + dictId + "/items/" + itemId + ":enable", null, idem).getStatusCode().value());

        // 有项字典禁删 409；删项后字典可删
        assertEquals(409, adminDelete(root.accessToken(),
                "/api/v1/admin/dicts/" + dictId, idem).getStatusCode().value());
        assertEquals(204, adminDelete(root.accessToken(),
                "/api/v1/admin/dicts/" + dictId + "/items/" + itemId, idem).getStatusCode().value());
        assertEquals(204, adminDelete(root.accessToken(),
                "/api/v1/admin/dicts/" + dictId, idem).getStatusCode().value());
        assertEquals(404, adminGet(root.accessToken(), "/api/v1/admin/dicts/" + dictId).getStatusCode().value());

        // 审计闭环
        JsonNode audits = dataNode(adminGet(root.accessToken(),
                "/api/v1/admin/audit-logs?action=dict.create")).path("items");
        boolean auditFound = false;
        for (JsonNode a : audits) {
            if (a.path("resource").asText().equals("dict:" + dictCode)) {
                auditFound = true;
            }
        }
        assertTrue(auditFound, "dict.create 审计应落库: " + audits);
    }

    // ---------- 6. 系统概览 ----------

    @Test
    void systemOverviewReadableByAdminAndAuditor() {
        Session root = login("platform-root", "Boot-Strap-1x");
        Session auditor = assignRoleDirect(newUser("acovauditor"), "platform_auditor");
        Session plain = newUser("acovplain");

        for (Session actor : new Session[] { root, auditor }) {
            ResponseEntity<String> resp = adminGet(actor.accessToken(), "/api/v1/admin/system/overview");
            assertEquals(200, resp.getStatusCode().value(), "admin/auditor 应可读概览: " + resp.getBody());
            JsonNode data = dataNode(resp);
            assertTrue(data.path("counts").path("users").asLong() > 0);
            assertTrue(data.path("counts").hasNonNull("organizations"));
            assertTrue(data.path("counts").hasNonNull("repositories"));
            assertTrue(data.path("counts").hasNonNull("auditLogs"));
            assertEquals("ok", data.path("healthStatus").asText());
            assertEquals("ok", data.path("healthChecks").path("database").asText());
        }
        assertEquals(403, adminGet(plain.accessToken(), "/api/v1/admin/system/overview").getStatusCode().value());
    }

    // ---------- 7. 审计 from/to 过滤 ----------

    @Test
    void auditLogTimeRangeFilter() {
        Session root = login("platform-root", "Boot-Strap-1x");
        Session registered = newUser("actimeuser");

        JsonNode all = dataNode(adminGet(root.accessToken(),
                "/api/v1/admin/audit-logs?actor=" + registered.username() + "&action=user.register")).path("items");
        assertTrue(all.size() >= 1, "应命中注册审计");
        String createdAt = all.get(0).path("createdAt").asText();
        assertNotNull(createdAt);

        // from 在事件之前：命中；from/to 在过去窗口：为空
        JsonNode after = dataNode(adminGet(root.accessToken(),
                "/api/v1/admin/audit-logs?actor=" + registered.username()
                        + "&action=user.register&from=2000-01-01T00:00:00Z"))
                .path("items");
        assertTrue(after.size() >= 1, "from 早于事件应命中");
        JsonNode pastWindow = dataNode(adminGet(root.accessToken(),
                "/api/v1/admin/audit-logs?actor=" + registered.username() + "&action=user.register"
                        + "&from=2000-01-01T00:00:00Z&to=2000-01-02T00:00:00Z")).path("items");
        assertEquals(0, pastWindow.size(), "过去窗口应无命中");

        // 非法格式 400
        assertEquals(400, adminGet(root.accessToken(),
                "/api/v1/admin/audit-logs?from=not-a-time").getStatusCode().value());
    }

    // ---------- 助手 ----------

    /** 直接经仓库指派角色（模拟存量/前置条件），返回该用户会话。 */
    private Session assignRoleDirect(Session session, String roleCode) {
        SysRoleEntity role = roleRepo.findByCode(roleCode).orElseThrow();
        UserEntity user = users.findByPublicId(UUID.fromString(session.userPublicId())).orElseThrow();
        SysUserRoleEntity assignment = new SysUserRoleEntity();
        assignment.setUserId(user.getId());
        assignment.setRoleId(role.getId());
        userRoleRepo.save(assignment);
        return session;
    }

    /** 吊销除指定用户外的全部活跃 platform_admin 指派（共享测试库隔离前置）。 */
    private void revokeAdminsExcept(Session keep) {
        SysRoleEntity role = roleRepo.findByCode("platform_admin").orElseThrow();
        UserEntity keepUser = users.findByPublicId(UUID.fromString(keep.userPublicId())).orElseThrow();
        for (SysUserRoleEntity a : userRoleRepo.findByRoleIdAndRevokedAtIsNull(role.getId())) {
            if (!a.getUserId().equals(keepUser.getId())) {
                a.setRevokedAt(java.time.OffsetDateTime.now());
                userRoleRepo.save(a);
            }
        }
    }

    private JsonNode findRole(Session root, String code) {
        JsonNode items = dataNode(adminGet(root.accessToken(), "/api/v1/admin/roles")).path("items");
        for (JsonNode r : items) {
            if (code.equals(r.path("code").asText())) {
                return r;
            }
        }
        throw new AssertionError("角色列表缺少 " + code + ": " + items);
    }

    private ResponseEntity<String> adminGet(String token, String path) {
        HttpHeaders h = token == null ? ipHeaders() : bearer(token);
        return rest.exchange(path, HttpMethod.GET, new HttpEntity<>(h), String.class);
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
