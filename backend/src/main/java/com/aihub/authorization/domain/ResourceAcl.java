/*
 * 功能: 资源 ACL 领域对象，承载 (资源类型,资源ID,主体) 的一组显式权限授予。
 * 时间: 2026-06-30
 * 作者: AxeXie
 */
package com.aihub.authorization.domain;

import java.time.Instant;
import java.util.List;
import java.util.Set;

/**
 * 资源 ACL 领域对象。
 *
 * <p>一个 {@code aclId} 聚合同一 (资源类型,资源ID,主体) 上的多条权限授予；对应契约
 * {@code ResourceAclView.permissionCodes} 列表。底层每条权限为一行，共享 acl_id。
 *
 * @param aclId        ACL 业务 ID
 * @param resourceType 资源类型（如 ASSET）
 * @param resourceId   资源业务 ID
 * @param principalId  被授权主体 ID
 * @param permissions  授予的权限编码集合
 * @param createdBy    创建者主体 ID
 * @param createdAt    创建时间
 */
public record ResourceAcl(String aclId, String resourceType, String resourceId,
                          String principalId, Set<String> permissions,
                          String createdBy, Instant createdAt) {

    /**
     * @return 权限编码列表（稳定顺序，便于视图序列化）
     */
    public List<String> permissionList() {
        return permissions == null ? List.of() : List.copyOf(permissions);
    }
}
