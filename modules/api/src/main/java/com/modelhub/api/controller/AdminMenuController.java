package com.modelhub.api.controller;

import com.modelhub.api.support.Principals;
import com.modelhub.identity.security.CurrentPrincipal;
import com.modelhub.identity.service.SysMenuService;
import com.modelhub.identity.service.SysMenuService.MenuView;
import com.modelhub.shared.web.ApiEnvelope;
import com.modelhub.shared.web.ETags;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

/**
 * 管理端菜单端点（字典统一+菜单权限重构计划 §五）：菜单树 CRUD 与启停用。
 * 服务层按权限点 admin:menu:view/manage 校验；写操作要求 Idempotency-Key 并回显。
 */
@RestController
@RequestMapping("/api/v1/admin/menus")
public class AdminMenuController {

    /** POST /admin/menus 请求体。 */
    public record CreateMenuBody(String code, String nameZh, String nameEn, String parentCode,
                                 String menuType, String path, String permissionCode, Integer sortOrder) {}

    /** PATCH /admin/menus/{code} 请求体：字段为 null 表示不修改（code 不可改；空串清除 path/permissionCode/parentCode）。 */
    public record UpdateMenuBody(String nameZh, String nameEn, String parentCode, String path,
                                 String permissionCode, Integer sortOrder) {}

    private final SysMenuService menuService;

    public AdminMenuController(SysMenuService menuService) {
        this.menuService = menuService;
    }

    @GetMapping
    public ResponseEntity<ApiEnvelope<Map<String, Object>>> list(HttpServletRequest request) {
        CurrentPrincipal actor = Principals.requireCurrent(request);
        List<MenuView> menus = menuService.listAll(actor);
        return ResponseEntity.ok(ApiEnvelope.ok(Map.of("items", menus)));
    }

    @PostMapping
    public ResponseEntity<ApiEnvelope<MenuView>> create(@RequestBody CreateMenuBody body,
                                                        @RequestHeader("Idempotency-Key") String idempotencyKey,
                                                        HttpServletRequest request) {
        CurrentPrincipal actor = Principals.requireCurrent(request);
        MenuView menu = menuService.create(actor, body.code(), body.nameZh(), body.nameEn(),
                body.parentCode(), body.menuType(), body.path(), body.permissionCode(), body.sortOrder());
        return ResponseEntity.status(HttpStatus.CREATED).header("Idempotency-Key", idempotencyKey)
                .body(ApiEnvelope.created(menu));
    }

    @PatchMapping("/{code:[a-z0-9_]+}")
    public ResponseEntity<ApiEnvelope<MenuView>> update(@PathVariable("code") String code,
                                                        @RequestBody UpdateMenuBody body,
                                                        @RequestHeader("If-Match") String ifMatch,
                                                        @RequestHeader("Idempotency-Key") String idempotencyKey,
                                                        HttpServletRequest request) {
        CurrentPrincipal actor = Principals.requireCurrent(request);
        MenuView menu = menuService.update(actor, code, body.nameZh(), body.nameEn(), body.parentCode(),
                body.path(), body.permissionCode(), body.sortOrder(), ifMatch);
        return ResponseEntity.ok().header("Idempotency-Key", idempotencyKey)
                .header("ETag", ETags.ofVersion(menu.version()))
                .body(ApiEnvelope.ok(menu));
    }

    @DeleteMapping("/{code:[a-z0-9_]+}")
    public ResponseEntity<Void> delete(@PathVariable("code") String code,
                                       @RequestHeader("Idempotency-Key") String idempotencyKey,
                                       HttpServletRequest request) {
        CurrentPrincipal actor = Principals.requireCurrent(request);
        menuService.delete(actor, code);
        return ResponseEntity.noContent().header("Idempotency-Key", idempotencyKey).build();
    }

    @PostMapping("/{code:[a-z0-9_]+}:disable")
    public ResponseEntity<ApiEnvelope<MenuView>> disable(@PathVariable("code") String code,
                                                         @RequestHeader("Idempotency-Key") String idempotencyKey,
                                                         HttpServletRequest request) {
        CurrentPrincipal actor = Principals.requireCurrent(request);
        MenuView menu = menuService.setMenuStatus(actor, code, false);
        return ResponseEntity.ok().header("Idempotency-Key", idempotencyKey)
                .body(ApiEnvelope.ok(menu));
    }

    @PostMapping("/{code:[a-z0-9_]+}:enable")
    public ResponseEntity<ApiEnvelope<MenuView>> enable(@PathVariable("code") String code,
                                                        @RequestHeader("Idempotency-Key") String idempotencyKey,
                                                        HttpServletRequest request) {
        CurrentPrincipal actor = Principals.requireCurrent(request);
        MenuView menu = menuService.setMenuStatus(actor, code, true);
        return ResponseEntity.ok().header("Idempotency-Key", idempotencyKey)
                .body(ApiEnvelope.ok(menu));
    }
}
