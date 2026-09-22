/*
 * 功能: Gitea 仓库存在性查询端口，供对账 Worker 校验 PG 记录与 Gitea 仓库一致性。
 * 时间: 2026-07-10
 * 作者: AxeXie
 */
package com.aihub.integration.gitea.infrastructure;

/**
 * Gitea 仓库存在性查询端口。
 *
 * <p>仅在 {@code aihub.gitea.enabled=true} 时由基础设施适配器注册；未启用时
 * {@link AssetRepositoryReconciler} 仅执行 PostgreSQL 侧名称/命名空间格式校验。
 */
public interface GiteaRepositoryExistencePort {

    /**
     * 判断 Gitea 中是否存在指定仓库。
     *
     * @param namespace 命名空间（组织或用户）
     * @param name      仓库名
     * @return {@code true} 表示仓库存在
     */
    boolean repositoryExists(String namespace, String name);
}
