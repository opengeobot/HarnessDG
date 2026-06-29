/*
 * 功能: 资产 Git 仓库开通端口，由集成适配器对接 Gitea 实现。
 * 时间: 2026-06-29
 * 作者: AxeXie
 */
package com.aihub.asset.domain;

/**
 * 资产 Git 仓库开通端口（Port）。
 *
 * <p>由资产模块定义、集成适配器实现（依赖方向：integration adapter → 业务模块 Port）。
 * 负责在 Gitea 创建仓库并写入初始卡片（README.md / asset.yaml），返回仓库引用。
 */
public interface AssetRepositoryProvisioner {

    /**
     * 开通资产仓库并写入初始卡片。实现需保证幂等或可重试，失败时抛出依赖异常。
     *
     * @param request 开通请求
     * @return 仓库引用
     */
    AssetRepositoryRef provision(ProvisionRequest request);

    /**
     * 仓库开通请求。
     *
     * @param namespace   命名空间（映射为仓库 owner/组织）
     * @param name        仓库名
     * @param type        资产类型
     * @param description 仓库描述
     * @param visibility  可见性（决定仓库私有/公开）
     * @param readme      初始 README.md 内容
     * @param assetYaml   初始 asset.yaml 内容
     */
    record ProvisionRequest(String namespace,
                            String name,
                            AssetType type,
                            String description,
                            Visibility visibility,
                            String readme,
                            String assetYaml) {
    }
}
