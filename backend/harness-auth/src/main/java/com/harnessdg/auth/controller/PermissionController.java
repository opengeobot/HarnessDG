/**
 * 功能：权限查询 REST 控制器
 * 时间：2026-05-07
 * 作者：AxeXie
 */
package com.harnessdg.auth.controller;

import com.harnessdg.auth.service.PermissionService;
import com.harnessdg.common.response.R;
import com.harnessdg.model.auth.dto.PermissionDTO;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/permissions")
@RequiredArgsConstructor
public class PermissionController {

    private final PermissionService permissionService;

    @GetMapping
    public R<List<PermissionDTO>> list(@RequestParam(required = false) Long roleId) {
        if (roleId != null) {
            return R.ok(permissionService.listByRoleId(roleId));
        }
        return R.ok(permissionService.listAll());
    }
}
