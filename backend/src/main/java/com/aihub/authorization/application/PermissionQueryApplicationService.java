/*
 * 功能: 权限查询应用服务，列出系统全部权限定义。
 * 时间: 2026-06-30
 * 作者: AxeXie
 */
package com.aihub.authorization.application;

import com.aihub.authorization.application.AuthorizationDtos.PermissionView;
import com.aihub.authorization.domain.PermissionRepository;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 权限查询应用服务。
 */
@Service
public class PermissionQueryApplicationService {

    private final PermissionRepository permissionRepository;

    public PermissionQueryApplicationService(PermissionRepository permissionRepository) {
        this.permissionRepository = permissionRepository;
    }

    /**
     * 列出系统全部权限定义。
     */
    @Transactional(readOnly = true)
    public List<PermissionView> listPermissions() {
        return permissionRepository.findAll().stream().map(PermissionView::from).toList();
    }
}
