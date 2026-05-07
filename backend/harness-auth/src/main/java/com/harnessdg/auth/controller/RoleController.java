/**
 * 功能：角色管理 REST 控制器
 * 时间：2026-05-07
 * 作者：AxeXie
 */
package com.harnessdg.auth.controller;

import com.harnessdg.audit.aspect.AuditAspect.Auditable;
import com.harnessdg.auth.service.RoleService;
import com.harnessdg.common.response.R;
import com.harnessdg.model.auth.dto.PermissionItem;
import com.harnessdg.model.auth.dto.RoleDTO;
import com.harnessdg.model.auth.dto.RoleUpsertRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/roles")
@RequiredArgsConstructor
public class RoleController {

    private final RoleService roleService;

    @GetMapping
    public R<List<RoleDTO>> list() {
        return R.ok(roleService.listRoles());
    }

    @GetMapping("/{id}")
    public R<RoleDTO> get(@PathVariable Long id) {
        return R.ok(roleService.getRole(id));
    }

    @PostMapping
    @PreAuthorize("hasRole('admin')")
    @Auditable(action = "create", resourceType = "role")
    public R<RoleDTO> create(@Valid @RequestBody RoleUpsertRequest request) {
        return R.ok(roleService.createRole(request));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasRole('admin')")
    @Auditable(action = "update", resourceType = "role")
    public R<RoleDTO> update(@PathVariable Long id, @Valid @RequestBody RoleUpsertRequest request) {
        return R.ok(roleService.updateRole(id, request));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('admin')")
    @Auditable(action = "delete", resourceType = "role")
    public R<Void> delete(@PathVariable Long id) {
        roleService.deleteRole(id);
        return R.ok();
    }

    @PutMapping("/{id}/permissions")
    @PreAuthorize("hasRole('admin')")
    @Auditable(action = "assign_permissions", resourceType = "role")
    public R<Void> assignPermissions(@PathVariable Long id,
                                      @RequestBody Map<String, List<PermissionItem>> body) {
        roleService.assignPermissions(id, body.get("permissions"));
        return R.ok();
    }
}
