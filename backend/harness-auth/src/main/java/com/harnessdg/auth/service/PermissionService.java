/**
 * 功能：权限查询服务接口
 * 时间：2026-05-07
 * 作者：AxeXie
 */
package com.harnessdg.auth.service;

import com.harnessdg.model.auth.dto.PermissionDTO;

import java.util.List;

public interface PermissionService {

    List<PermissionDTO> listByRoleId(Long roleId);

    List<PermissionDTO> listAll();
}
