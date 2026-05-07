/**
 * 功能：用户管理 REST 控制器
 * 时间：2026-05-07
 * 作者：AxeXie
 */
package com.harnessdg.auth.controller;

import com.harnessdg.audit.aspect.AuditAspect.Auditable;
import com.harnessdg.auth.service.UserService;
import com.harnessdg.common.page.PageRequest;
import com.harnessdg.common.page.PageResult;
import com.harnessdg.common.response.R;
import com.harnessdg.model.auth.dto.ChangePasswordRequest;
import com.harnessdg.model.auth.dto.UserCreateRequest;
import com.harnessdg.model.auth.dto.UserDTO;
import com.harnessdg.model.auth.dto.UserUpdateRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/users")
@RequiredArgsConstructor
public class UserController {

    private final UserService userService;

    @GetMapping
    @PreAuthorize("hasRole('admin')")
    public R<PageResult<UserDTO>> list(
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String status,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int pageSize) {
        PageRequest pr = new PageRequest();
        pr.setPage(page);
        pr.setPageSize(pageSize);
        return R.ok(userService.listUsers(keyword, status, pr));
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasRole('admin')")
    public R<UserDTO> get(@PathVariable Long id) {
        return R.ok(userService.getUser(id));
    }

    @PostMapping
    @PreAuthorize("hasRole('admin')")
    @Auditable(action = "create", resourceType = "user")
    public R<UserDTO> create(@Valid @RequestBody UserCreateRequest request) {
        return R.ok(userService.createUser(request));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasRole('admin')")
    @Auditable(action = "update", resourceType = "user")
    public R<UserDTO> update(@PathVariable Long id, @Valid @RequestBody UserUpdateRequest request) {
        return R.ok(userService.updateUser(id, request));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('admin')")
    @Auditable(action = "delete", resourceType = "user")
    public R<Void> delete(@PathVariable Long id) {
        userService.deleteUser(id);
        return R.ok();
    }

    @PutMapping("/{id}/status")
    @PreAuthorize("hasRole('admin')")
    @Auditable(action = "update_status", resourceType = "user")
    public R<Void> updateStatus(@PathVariable Long id, @RequestBody Map<String, String> body) {
        userService.updateStatus(id, body.get("status"));
        return R.ok();
    }

    @PutMapping("/{id}/password")
    @PreAuthorize("hasRole('admin')")
    @Auditable(action = "reset_password", resourceType = "user")
    public R<Void> resetPassword(@PathVariable Long id, @RequestBody Map<String, String> body) {
        userService.resetPassword(id, body.get("newPassword"));
        return R.ok();
    }

    @PutMapping("/me/password")
    @Auditable(action = "change_password", resourceType = "user")
    public R<Void> changeMyPassword(
            Authentication authentication,
            @Valid @RequestBody ChangePasswordRequest request) {
        String username = (String) authentication.getPrincipal();
        userService.changeMyPassword(username, request);
        return R.ok();
    }

    @PutMapping("/{id}/roles")
    @PreAuthorize("hasRole('admin')")
    @Auditable(action = "assign_roles", resourceType = "user")
    public R<Void> assignRoles(@PathVariable Long id, @RequestBody Map<String, List<Long>> body) {
        userService.assignRoles(id, body.get("roleIds"));
        return R.ok();
    }
}
