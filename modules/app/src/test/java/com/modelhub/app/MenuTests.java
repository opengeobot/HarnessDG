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

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 标准 RBAC 菜单权限集成测试（字典统一+菜单权限重构计划 §五/§八）：
 * /me/menus 按权限派生（auditor 只见授权菜单、普通用户空集、匿名 401）、
 * /admin/menus CRUD 守卫（权限码/父级/内置目录/有子禁删/启停用）与审计闭环。
 */
class MenuTests extends CatalogTestSupport {

    @Autowired
    private SysRoleRepository roleRepo;

    @Autowired
    private SysUserRoleRepository userRoleRepo;

    // ---------- 1. /me/menus 按权限派生 ----------

    @Test
    void meMenusDerivedFromGrantedPermissions() {
        Session root = login("platform-root", "Boot-Strap-1x");
        Session plain = newUser("mtplain");
        Session auditor = assignRoleDirect(newUser("mtauditor"), "platform_auditor");

        // 匿名 401
        ResponseEntity<String> anon = rest.exchange("/api/v1/me/menus", HttpMethod.GET,
                new HttpEntity<>(ipHeaders()), String.class);
        assertEquals(401, anon.getStatusCode().value());

        // platform_admin：种子 8 个菜单全见
        JsonNode adminItems = dataNode(getMenus(root.accessToken())).path("items");
        assertTrue(adminItems.size() >= 8, "platform_admin 应见全部种子菜单: " + adminItems);
        assertTrue(hasCode(adminItems, "admin_users"), "应含用户管理菜单");
        assertTrue(hasCode(adminItems, "admin_menus"), "应含菜单管理菜单");

        // auditor：V16 授 admin:system:view / admin:audit:view / admin:user:view（只读），V19 增授 admin:repo:view；
        // 应见对应只读菜单，但不见角色/字典/菜单管理等未授权项
        JsonNode auditorItems = dataNode(getMenus(auditor.accessToken())).path("items");
        assertTrue(hasCode(auditorItems, "admin_overview"), "auditor 应见系统概览: " + auditorItems);
        assertTrue(hasCode(auditorItems, "admin_users"), "auditor 应见用户管理（只读）: " + auditorItems);
        assertTrue(hasCode(auditorItems, "admin_repos"), "auditor 应见仓库运维: " + auditorItems);
        assertFalse(hasCode(auditorItems, "admin_roles"), "auditor 不应见角色权限");
        assertFalse(hasCode(auditorItems, "admin_menus"), "auditor 不应见菜单管理");
        // 目录节点不进入可见菜单（前端按固定结构组织）
        assertFalse(hasCode(auditorItems, "admin_console"), "directory 不应出现在 /me/menus");

        // 普通用户：无任何管理权限 → 空集
        JsonNode plainItems = dataNode(getMenus(plain.accessToken())).path("items");
        assertEquals(0, plainItems.size(), "普通用户可见菜单应为空: " + plainItems);
    }

    // ---------- 2. /admin/menus 访问控制 ----------

    @Test
    void adminMenusEndpointEnforcesPermissionCodes() {
        Session root = login("platform-root", "Boot-Strap-1x");
        Session plain = newUser("mtacplain");
        String idem = UUID.randomUUID().toString();

        assertEquals(403, menuGet(plain.accessToken()).getStatusCode().value());
        assertEquals(401, menuGet(null).getStatusCode().value());

        // 仅持 admin:menu:view 的自定义角色：可读不可写
        String roleCode = unique("mt_view_role_").toLowerCase();
        ResponseEntity<String> role = adminPost(root.accessToken(), "/api/v1/admin/roles",
                Map.of("code", roleCode, "description", "菜单只读角色",
                        "permissions", List.of("admin:menu:view")), idem);
        assertEquals(201, role.getStatusCode().value(), "创建角色失败: " + role.getBody());
        Session viewer = assignRoleDirect(newUser("mtviewer"), roleCode);

        assertEquals(200, menuGet(viewer.accessToken()).getStatusCode().value());
        assertEquals(403, menuPost(viewer.accessToken(),
                Map.of("code", unique("mt_deny_").toLowerCase(), "nameZh", "拒绝", "nameEn", "Deny",
                        "menuType", "menu"), idem).getStatusCode().value());

        // 清理自建角色（无活跃指派后方可删）
        ResponseEntity<String> revoked = adminPost(root.accessToken(),
                "/api/v1/admin/users/" + viewer.userPublicId() + "/roles/" + roleCode + ":revoke", null, idem);
        assertEquals(200, revoked.getStatusCode().value());
        assertEquals(204, adminDelete(root.accessToken(),
                "/api/v1/admin/roles/" + dataNode(role).path("id").asText(), idem).getStatusCode().value());
    }

    // ---------- 3. CRUD 守卫与审计 ----------

    @Test
    void menuCrudGuardsBuiltInProtectionAndAudit() {
        Session root = login("platform-root", "Boot-Strap-1x");
        String idem = UUID.randomUUID().toString();

        // 种子菜单树可见（含目录与菜单两类）
        JsonNode all = dataNode(menuGet(root.accessToken())).path("items");
        assertTrue(hasCode(all, "admin_console"), "应含内置目录: " + all);

        // 创建守卫：非法 code 400 / 未知权限码 400 / 父级非 directory 400
        assertEquals(400, menuPost(root.accessToken(),
                Map.of("code", "BadCode", "nameZh", "非法", "nameEn", "Bad", "menuType", "menu"),
                idem).getStatusCode().value());
        assertEquals(400, menuPost(root.accessToken(),
                Map.of("code", unique("mt_noperm_").toLowerCase(), "nameZh", "无权限点", "nameEn", "NoPerm",
                        "menuType", "menu", "permissionCode", "not:a:perm"), idem).getStatusCode().value());
        assertEquals(400, menuPost(root.accessToken(),
                Map.of("code", unique("mt_badpar_").toLowerCase(), "nameZh", "父级非法", "nameEn", "BadParent",
                        "menuType", "menu", "parentCode", "admin_users"), idem).getStatusCode().value());

        // 正常创建：目录 + 菜单
        String dirCode = unique("mt_dir_").toLowerCase();
        ResponseEntity<String> dir = menuPost(root.accessToken(),
                Map.of("code", dirCode, "nameZh", "测试目录", "nameEn", "Test Dir",
                        "menuType", "directory", "path", "/admin/" + dirCode, "sortOrder", 90), idem);
        assertEquals(201, dir.getStatusCode().value(), "创建目录失败: " + dir.getBody());
        assertEquals(idem, dir.getHeaders().getFirst("Idempotency-Key"));

        String menuCode = unique("mt_menu_").toLowerCase();
        ResponseEntity<String> menu = menuPost(root.accessToken(),
                Map.of("code", menuCode, "nameZh", "测试菜单", "nameEn", "Test Menu",
                        "menuType", "menu", "parentCode", dirCode, "path", "/admin/" + menuCode,
                        "permissionCode", "admin:menu:view", "sortOrder", 1), idem);
        assertEquals(201, menu.getStatusCode().value(), "创建菜单失败: " + menu.getBody());
        long version = dataNode(menu).path("version").asLong();

        // 重复 code 409
        assertEquals(409, menuPost(root.accessToken(),
                Map.of("code", menuCode, "nameZh", "重复", "nameEn", "Dup", "menuType", "menu"),
                idem).getStatusCode().value());

        // 条件更新：错误 ETag 412；正确 ETag 200 且版本递增
        ResponseEntity<String> stale = menuPatch(root.accessToken(), menuCode,
                Map.of("nameEn", "Renamed"), "\"deadbeef\"", idem);
        assertEquals(412, stale.getStatusCode().value());
        ResponseEntity<String> patched = menuPatch(root.accessToken(), menuCode,
                Map.of("nameEn", "Renamed"), etagOfVersion(version), idem);
        assertEquals(200, patched.getStatusCode().value(), "更新失败: " + patched.getBody());
        assertEquals("Renamed", dataNode(patched).path("nameEn").asText());
        assertEquals(version + 1, dataNode(patched).path("version").asLong());

        // 启停用：重复同态 409
        ResponseEntity<String> off = menuPost(root.accessToken(),
                "/api/v1/admin/menus/" + menuCode + ":disable", null, idem);
        assertEquals(200, off.getStatusCode().value());
        assertEquals("disabled", dataNode(off).path("status").asText());
        assertEquals(409, menuPost(root.accessToken(),
                "/api/v1/admin/menus/" + menuCode + ":disable", null, idem).getStatusCode().value());
        ResponseEntity<String> on = menuPost(root.accessToken(),
                "/api/v1/admin/menus/" + menuCode + ":enable", null, idem);
        assertEquals(200, on.getStatusCode().value());
        assertEquals("active", dataNode(on).path("status").asText());

        // 停用菜单后不再出现在 /me/menus
        assertEquals(200, menuPost(root.accessToken(),
                "/api/v1/admin/menus/" + menuCode + ":disable", null, idem).getStatusCode().value());
        assertFalse(hasCode(dataNode(getMenus(root.accessToken())).path("items"), menuCode),
                "停用菜单不应出现在可见菜单");
        assertEquals(200, menuPost(root.accessToken(),
                "/api/v1/admin/menus/" + menuCode + ":enable", null, idem).getStatusCode().value());

        // 删除守卫：内置目录 409；有子节点 409；叶子可删后目录可删
        assertEquals(409, menuDelete(root.accessToken(), "admin_console", idem).getStatusCode().value());
        assertEquals(409, menuDelete(root.accessToken(), dirCode, idem).getStatusCode().value());
        assertEquals(204, menuDelete(root.accessToken(), menuCode, idem).getStatusCode().value());
        assertEquals(204, menuDelete(root.accessToken(), dirCode, idem).getStatusCode().value());
        assertEquals(404, menuDelete(root.accessToken(), menuCode, idem).getStatusCode().value());

        // 审计闭环
        JsonNode audits = dataNode(adminGet(root.accessToken(),
                "/api/v1/admin/audit-logs?action=menu.create&actor=" + root.username())).path("items");
        boolean auditFound = false;
        for (JsonNode a : audits) {
            if (a.path("resource").asText().equals("menu:" + menuCode)) {
                auditFound = true;
            }
        }
        assertTrue(auditFound, "menu.create 审计应落库: " + audits);
    }

    // ---------- 助手 ----------

    private Session assignRoleDirect(Session session, String roleCode) {
        SysRoleEntity role = roleRepo.findByCode(roleCode).orElseThrow();
        UserEntity user = users.findByPublicId(UUID.fromString(session.userPublicId())).orElseThrow();
        SysUserRoleEntity assignment = new SysUserRoleEntity();
        assignment.setUserId(user.getId());
        assignment.setRoleId(role.getId());
        userRoleRepo.save(assignment);
        return session;
    }

    private static boolean hasCode(JsonNode items, String code) {
        for (JsonNode it : items) {
            if (code.equals(it.path("code").asText())) {
                return true;
            }
        }
        return false;
    }

    private ResponseEntity<String> getMenus(String token) {
        HttpHeaders h = bearer(token);
        return rest.exchange("/api/v1/me/menus", HttpMethod.GET, new HttpEntity<>(h), String.class);
    }

    private ResponseEntity<String> menuGet(String token) {
        HttpHeaders h = token == null ? ipHeaders() : bearer(token);
        return rest.exchange("/api/v1/admin/menus", HttpMethod.GET, new HttpEntity<>(h), String.class);
    }

    private ResponseEntity<String> menuPost(String token, Object body, String idem) {
        return menuPost(token, "/api/v1/admin/menus", body, idem);
    }

    private ResponseEntity<String> menuPost(String token, String path, Object body, String idem) {
        HttpHeaders h = bearer(token);
        h.setContentType(MediaType.APPLICATION_JSON);
        h.add("Idempotency-Key", idem);
        return rest.exchange(path, HttpMethod.POST, new HttpEntity<>(body, h), String.class);
    }

    private ResponseEntity<String> menuPatch(String token, String code, Object body,
                                             String ifMatch, String idem) {
        HttpHeaders h = bearer(token);
        h.setContentType(MediaType.APPLICATION_JSON);
        h.add("Idempotency-Key", idem);
        h.add("If-Match", ifMatch);
        return rest.exchange("/api/v1/admin/menus/" + code, HttpMethod.PATCH,
                new HttpEntity<>(body, h), String.class);
    }

    private ResponseEntity<String> menuDelete(String token, String code, String idem) {
        HttpHeaders h = bearer(token);
        h.add("Idempotency-Key", idem);
        return rest.exchange("/api/v1/admin/menus/" + code, HttpMethod.DELETE,
                new HttpEntity<>(h), String.class);
    }

    private ResponseEntity<String> adminGet(String token, String path) {
        HttpHeaders h = bearer(token);
        return rest.exchange(path, HttpMethod.GET, new HttpEntity<>(h), String.class);
    }

    private ResponseEntity<String> adminPost(String token, String path, Object body, String idem) {
        HttpHeaders h = bearer(token);
        h.setContentType(MediaType.APPLICATION_JSON);
        h.add("Idempotency-Key", idem);
        return rest.exchange(path, HttpMethod.POST, new HttpEntity<>(body, h), String.class);
    }

    private ResponseEntity<String> adminDelete(String token, String path, String idem) {
        HttpHeaders h = bearer(token);
        h.add("Idempotency-Key", idem);
        return rest.exchange(path, HttpMethod.DELETE, new HttpEntity<>(h), String.class);
    }
}
