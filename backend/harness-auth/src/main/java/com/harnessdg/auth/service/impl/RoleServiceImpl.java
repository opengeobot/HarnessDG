/**
 * 功能：角色管理服务实现
 * 时间：2026-05-07
 * 作者：AxeXie
 */
package com.harnessdg.auth.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.harnessdg.auth.mapper.SysPermissionMapper;
import com.harnessdg.auth.mapper.SysRoleMapper;
import com.harnessdg.auth.mapper.SysUserRoleMapper;
import com.harnessdg.auth.service.RoleService;
import com.harnessdg.common.exception.BizException;
import com.harnessdg.common.response.ErrorCode;
import com.harnessdg.model.auth.dto.PermissionDTO;
import com.harnessdg.model.auth.dto.PermissionItem;
import com.harnessdg.model.auth.dto.RoleDTO;
import com.harnessdg.model.auth.dto.RoleUpsertRequest;
import com.harnessdg.model.auth.entity.SysPermission;
import com.harnessdg.model.auth.entity.SysRole;
import com.harnessdg.model.auth.entity.SysUserRole;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collections;
import java.util.List;

@Service
@RequiredArgsConstructor
public class RoleServiceImpl implements RoleService {

    private final SysRoleMapper roleMapper;
    private final SysPermissionMapper permissionMapper;
    private final SysUserRoleMapper userRoleMapper;

    @Override
    public List<RoleDTO> listRoles() {
        LambdaQueryWrapper<SysRole> wrapper = new LambdaQueryWrapper<>();
        wrapper.orderByAsc(SysRole::getId);
        List<SysRole> roles = roleMapper.selectList(wrapper);
        return roles.stream().map(this::toDTO).toList();
    }

    @Override
    public RoleDTO getRole(Long id) {
        SysRole role = roleMapper.selectById(id);
        if (role == null) {
            throw new BizException(ErrorCode.ROLE_NOT_FOUND);
        }
        RoleDTO dto = toDTO(role);
        dto.setPermissions(listPermissionsByRole(id));
        return dto;
    }

    @Override
    @Transactional
    public RoleDTO createRole(RoleUpsertRequest request) {
        LambdaQueryWrapper<SysRole> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(SysRole::getCode, request.getCode());
        if (roleMapper.selectCount(wrapper) > 0) {
            throw new BizException(ErrorCode.ROLE_CODE_EXISTS);
        }
        SysRole role = new SysRole();
        role.setCode(request.getCode());
        role.setName(request.getName());
        role.setDescription(request.getDescription());
        role.setIsSystem(false);
        role.setStatus(request.getStatus());
        roleMapper.insert(role);
        return toDTO(role);
    }

    @Override
    @Transactional
    public RoleDTO updateRole(Long id, RoleUpsertRequest request) {
        SysRole role = roleMapper.selectById(id);
        if (role == null) {
            throw new BizException(ErrorCode.ROLE_NOT_FOUND);
        }
        if (Boolean.TRUE.equals(role.getIsSystem())
                && !role.getCode().equals(request.getCode())) {
            throw new BizException(ErrorCode.SYSTEM_ROLE_CANNOT_MODIFY, "System role code cannot be changed");
        }
        role.setName(request.getName());
        role.setDescription(request.getDescription());
        role.setStatus(request.getStatus());
        roleMapper.updateById(role);
        return toDTO(role);
    }

    @Override
    @Transactional
    public void deleteRole(Long id) {
        SysRole role = roleMapper.selectById(id);
        if (role == null) {
            throw new BizException(ErrorCode.ROLE_NOT_FOUND);
        }
        if (Boolean.TRUE.equals(role.getIsSystem())) {
            throw new BizException(ErrorCode.SYSTEM_ROLE_CANNOT_DELETE);
        }
        roleMapper.deleteById(id);
        // 软删关联权限与用户-角色
        LambdaUpdateWrapper<SysPermission> pw = new LambdaUpdateWrapper<>();
        pw.eq(SysPermission::getRoleId, id);
        SysPermission delP = new SysPermission();
        delP.setIsDeleted(true);
        permissionMapper.update(delP, pw);

        LambdaUpdateWrapper<SysUserRole> uw = new LambdaUpdateWrapper<>();
        uw.eq(SysUserRole::getRoleId, id);
        SysUserRole delU = new SysUserRole();
        delU.setIsDeleted(true);
        userRoleMapper.update(delU, uw);
    }

    @Override
    @Transactional
    public void assignPermissions(Long id, List<PermissionItem> permissions) {
        SysRole role = roleMapper.selectById(id);
        if (role == null) {
            throw new BizException(ErrorCode.ROLE_NOT_FOUND);
        }
        // 软删旧权限
        LambdaUpdateWrapper<SysPermission> uw = new LambdaUpdateWrapper<>();
        uw.eq(SysPermission::getRoleId, id);
        SysPermission del = new SysPermission();
        del.setIsDeleted(true);
        permissionMapper.update(del, uw);

        if (permissions == null) {
            return;
        }
        for (PermissionItem item : permissions) {
            SysPermission p = new SysPermission();
            p.setRoleId(id);
            p.setResourceType(item.getResourceType());
            p.setResourceId(item.getResourceId());
            p.setAction(item.getAction());
            p.setEffect(item.getEffect() == null ? "allow" : item.getEffect());
            permissionMapper.insert(p);
        }
    }

    private List<PermissionDTO> listPermissionsByRole(Long roleId) {
        LambdaQueryWrapper<SysPermission> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(SysPermission::getRoleId, roleId);
        List<SysPermission> list = permissionMapper.selectList(wrapper);
        return list.stream().map(p -> {
            PermissionDTO dto = new PermissionDTO();
            dto.setId(p.getId());
            dto.setRoleId(p.getRoleId());
            dto.setResourceType(p.getResourceType());
            dto.setResourceId(p.getResourceId());
            dto.setAction(p.getAction());
            dto.setEffect(p.getEffect());
            return dto;
        }).toList();
    }

    private RoleDTO toDTO(SysRole role) {
        RoleDTO dto = new RoleDTO();
        dto.setId(role.getId());
        dto.setCode(role.getCode());
        dto.setName(role.getName());
        dto.setDescription(role.getDescription());
        dto.setIsSystem(role.getIsSystem());
        dto.setStatus(role.getStatus());

        LambdaQueryWrapper<SysPermission> pw = new LambdaQueryWrapper<>();
        pw.eq(SysPermission::getRoleId, role.getId());
        dto.setPermissionCount(Math.toIntExact(permissionMapper.selectCount(pw)));

        LambdaQueryWrapper<SysUserRole> uw = new LambdaQueryWrapper<>();
        uw.eq(SysUserRole::getRoleId, role.getId());
        dto.setUserCount(Math.toIntExact(userRoleMapper.selectCount(uw)));

        dto.setPermissions(Collections.emptyList());
        return dto;
    }
}
