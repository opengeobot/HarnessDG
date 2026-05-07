/**
 * 功能：权限查询服务实现
 * 时间：2026-05-07
 * 作者：AxeXie
 */
package com.harnessdg.auth.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.harnessdg.auth.mapper.SysPermissionMapper;
import com.harnessdg.auth.service.PermissionService;
import com.harnessdg.model.auth.dto.PermissionDTO;
import com.harnessdg.model.auth.entity.SysPermission;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public class PermissionServiceImpl implements PermissionService {

    private final SysPermissionMapper permissionMapper;

    @Override
    public List<PermissionDTO> listByRoleId(Long roleId) {
        LambdaQueryWrapper<SysPermission> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(SysPermission::getRoleId, roleId);
        return permissionMapper.selectList(wrapper).stream().map(this::toDTO).toList();
    }

    @Override
    public List<PermissionDTO> listAll() {
        List<SysPermission> list = permissionMapper.selectList(null);
        return list.stream().map(this::toDTO).toList();
    }

    private PermissionDTO toDTO(SysPermission p) {
        PermissionDTO dto = new PermissionDTO();
        dto.setId(p.getId());
        dto.setRoleId(p.getRoleId());
        dto.setResourceType(p.getResourceType());
        dto.setResourceId(p.getResourceId());
        dto.setAction(p.getAction());
        dto.setEffect(p.getEffect());
        return dto;
    }
}
