/**
 * 功能：角色管理服务接口
 * 时间：2026-05-07
 * 作者：AxeXie
 */
package com.harnessdg.auth.service;

import com.harnessdg.model.auth.dto.PermissionItem;
import com.harnessdg.model.auth.dto.RoleDTO;
import com.harnessdg.model.auth.dto.RoleUpsertRequest;

import java.util.List;

public interface RoleService {

    List<RoleDTO> listRoles();

    RoleDTO getRole(Long id);

    RoleDTO createRole(RoleUpsertRequest request);

    RoleDTO updateRole(Long id, RoleUpsertRequest request);

    void deleteRole(Long id);

    void assignPermissions(Long id, List<PermissionItem> permissions);
}
