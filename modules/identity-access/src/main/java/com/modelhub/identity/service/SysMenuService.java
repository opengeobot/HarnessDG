package com.modelhub.identity.service;

import com.modelhub.identity.domain.SysMenuEntity;
import com.modelhub.identity.repo.SysMenuRepository;
import com.modelhub.identity.repo.SysPermissionRepository;
import com.modelhub.identity.security.CurrentPrincipal;
import com.modelhub.shared.error.ApiException;
import com.modelhub.shared.error.ErrorCode;
import com.modelhub.shared.web.ETags;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * 系统菜单服务（字典统一+菜单权限重构计划 §五）：菜单按权限派生可见性
 * （/me/menus），管理面 CRUD 走权限点 admin:menu:view/manage；写操作落审计。
 * 菜单是权限的呈现载体：授权真相源仍是角色-权限，菜单仅挂载权限点。
 */
@Service
public class SysMenuService {

    private static final Pattern MENU_CODE_PATTERN = Pattern.compile("^[a-z][a-z0-9_]{2,63}$");
    private static final Set<String> MENU_TYPES = Set.of("directory", "menu", "button");
    /** 内置菜单目录（禁删）。 */
    private static final String BUILT_IN_ROOT = "admin_console";

    /** 契约 SysMenu schema（管理面全量视图）。 */
    public record MenuView(String code, String nameZh, String nameEn, String parentCode,
                           String menuType, String path, String permissionCode,
                           int sortOrder, String status, long version) {}

    /** /me/menus 派生结果：仅含当前用户可见的菜单项（按语言择显由前端完成）。 */
    public record VisibleMenu(String code, String nameZh, String nameEn, String path, int sortOrder) {}

    private final SysMenuRepository menus;
    private final SysPermissionRepository permissions;
    private final AuditService auditService;

    public SysMenuService(SysMenuRepository menus, SysPermissionRepository permissions,
                          AuditService auditService) {
        this.menus = menus;
        this.permissions = permissions;
        this.auditService = auditService;
    }

    // ---------- 当前用户可见菜单（登录态即可） ----------

    @Transactional(readOnly = true)
    public List<VisibleMenu> menuTreeOf(CurrentPrincipal actor) {
        requireAuthenticated(actor);
        return menus.findAllByOrderBySortOrderAscIdAsc().stream()
                .filter(m -> "menu".equals(m.getMenuType()))
                .filter(m -> "active".equals(m.getStatus()))
                .filter(m -> m.getPermissionCode() == null || actor.hasPermission(m.getPermissionCode()))
                .map(m -> new VisibleMenu(m.getCode(), m.getNameZh(), m.getNameEn(), m.getPath(), m.getSortOrder()))
                .toList();
    }

    // ---------- 管理面 CRUD（权限点校验） ----------

    @Transactional(readOnly = true)
    public List<MenuView> listAll(CurrentPrincipal actor) {
        requirePermission(actor, "admin:menu:view");
        return menus.findAllByOrderBySortOrderAscIdAsc().stream().map(this::toView).toList();
    }

    @Transactional
    public MenuView create(CurrentPrincipal actor, String code, String nameZh, String nameEn,
                           String parentCode, String menuType, String path,
                           String permissionCode, Integer sortOrder) {
        requirePermission(actor, "admin:menu:manage");
        if (code == null || !MENU_CODE_PATTERN.matcher(code).matches()) {
            throw ApiException.badRequest("菜单 code 必须为 3-64 位小写字母/数字/下划线，且以字母开头",
                    List.of(new ApiException.Detail("code", "invalid_format")));
        }
        if (nameZh == null || nameZh.isBlank() || nameEn == null || nameEn.isBlank()) {
            throw ApiException.badRequest("菜单中英文名称均不能为空",
                    List.of(new ApiException.Detail("name", "required")));
        }
        if (menuType == null || !MENU_TYPES.contains(menuType)) {
            throw ApiException.badRequest("菜单类型必须为 directory/menu/button",
                    List.of(new ApiException.Detail("menuType", "invalid_type")));
        }
        if (menus.existsByCode(code)) {
            throw new ApiException(ErrorCode.CONFLICT, "菜单 code 已存在");
        }
        requirePermissionExists(permissionCode);
        requireParentDirectory(parentCode, null);
        SysMenuEntity menu = new SysMenuEntity();
        menu.setCode(code);
        menu.setNameZh(nameZh.trim());
        menu.setNameEn(nameEn.trim());
        menu.setParentCode(normalizeBlank(parentCode));
        menu.setMenuType(menuType);
        menu.setPath(normalizeBlank(path));
        menu.setPermissionCode(normalizeBlank(permissionCode));
        menu.setSortOrder(sortOrder == null ? 0 : sortOrder);
        menus.save(menu);
        auditService.appendSimple(actor.username(), "menu.create", "menu:" + code, "success");
        return toView(menu);
    }

    /** 更新菜单：code 不可改（稳定键）；null 字段不修改；permissionCode/parentCode 空串清除。If-Match 条件更新。 */
    @Transactional
    public MenuView update(CurrentPrincipal actor, String code, String nameZh, String nameEn,
                           String parentCode, String path, String permissionCode,
                           Integer sortOrder, String ifMatch) {
        requirePermission(actor, "admin:menu:manage");
        SysMenuEntity menu = requireMenu(code);
        ETags.requireMatch(ifMatch, ETags.ofVersion(menu.getVersion()), "菜单");
        if (nameZh != null && !nameZh.isBlank()) {
            menu.setNameZh(nameZh.trim());
        }
        if (nameEn != null && !nameEn.isBlank()) {
            menu.setNameEn(nameEn.trim());
        }
        if (path != null) {
            menu.setPath(normalizeBlank(path));
        }
        if (permissionCode != null) {
            if (!permissionCode.isBlank()) {
                requirePermissionExists(permissionCode);
            }
            menu.setPermissionCode(normalizeBlank(permissionCode));
        }
        if (parentCode != null) {
            if (!parentCode.isBlank()) {
                requireParentDirectory(parentCode, code);
            }
            menu.setParentCode(normalizeBlank(parentCode));
        }
        if (sortOrder != null) {
            menu.setSortOrder(sortOrder);
        }
        menu.setVersion(menu.getVersion() + 1);
        menus.save(menu);
        auditService.appendSimple(actor.username(), "menu.update", "menu:" + code, "success");
        return toView(menu);
    }

    @Transactional
    public void delete(CurrentPrincipal actor, String code) {
        requirePermission(actor, "admin:menu:manage");
        SysMenuEntity menu = requireMenu(code);
        if (BUILT_IN_ROOT.equals(menu.getCode())) {
            throw new ApiException(ErrorCode.CONFLICT, "内置菜单目录不可删除");
        }
        if (menus.countByParentCode(menu.getCode()) > 0) {
            throw new ApiException(ErrorCode.CONFLICT, "菜单仍有子节点，不可删除");
        }
        menus.delete(menu);
        auditService.appendSimple(actor.username(), "menu.delete", "menu:" + code, "success");
    }

    @Transactional
    public MenuView setMenuStatus(CurrentPrincipal actor, String code, boolean active) {
        requirePermission(actor, "admin:menu:manage");
        SysMenuEntity menu = requireMenu(code);
        String target = active ? "active" : "disabled";
        if (target.equals(menu.getStatus())) {
            throw new ApiException(ErrorCode.CONFLICT, active ? "菜单已启用" : "菜单已停用");
        }
        menu.setStatus(target);
        menu.setVersion(menu.getVersion() + 1);
        menus.save(menu);
        auditService.appendSimple(actor.username(), active ? "menu.enable" : "menu.disable",
                "menu:" + code, "success");
        return toView(menu);
    }

    // ---------- 内部 ----------

    private MenuView toView(SysMenuEntity m) {
        return new MenuView(m.getCode(), m.getNameZh(), m.getNameEn(), m.getParentCode(),
                m.getMenuType(), m.getPath(), m.getPermissionCode(),
                m.getSortOrder(), m.getStatus(), m.getVersion());
    }

    private SysMenuEntity requireMenu(String code) {
        return menus.findByCode(code)
                .orElseThrow(() -> new ApiException(ErrorCode.RESOURCE_NOT_FOUND, "菜单不存在"));
    }

    private void requirePermissionExists(String permissionCode) {
        if (permissionCode == null || permissionCode.isBlank()) {
            return;
        }
        if (permissions.findByCode(permissionCode.trim()).isEmpty()) {
            throw ApiException.badRequest("权限码未注册: " + permissionCode,
                    List.of(new ApiException.Detail("permissionCode", "unknown_permission")));
        }
    }

    /** 父级必须存在且为 directory；禁止挂到自身。 */
    private void requireParentDirectory(String parentCode, String selfCode) {
        String pc = normalizeBlank(parentCode);
        if (pc == null) {
            return;
        }
        if (pc.equals(selfCode)) {
            throw ApiException.badRequest("父级菜单不能为自身",
                    List.of(new ApiException.Detail("parentCode", "self_parent")));
        }
        SysMenuEntity parent = menus.findByCode(pc)
                .orElseThrow(() -> ApiException.badRequest("父级菜单不存在",
                        List.of(new ApiException.Detail("parentCode", "unknown_parent"))));
        if (!"directory".equals(parent.getMenuType())) {
            throw ApiException.badRequest("父级菜单必须为目录（directory）",
                    List.of(new ApiException.Detail("parentCode", "parent_not_directory")));
        }
    }

    private static String normalizeBlank(String v) {
        return v == null || v.isBlank() ? null : v.trim();
    }

    private static void requireAuthenticated(CurrentPrincipal actor) {
        if (actor == null) {
            throw new ApiException(ErrorCode.UNAUTHENTICATED, "未认证或凭证已过期");
        }
    }

    private static void requirePermission(CurrentPrincipal actor, String code) {
        requireAuthenticated(actor);
        if (!actor.hasPermission(code)) {
            throw new ApiException(ErrorCode.FORBIDDEN, "缺少权限: " + code);
        }
    }
}
