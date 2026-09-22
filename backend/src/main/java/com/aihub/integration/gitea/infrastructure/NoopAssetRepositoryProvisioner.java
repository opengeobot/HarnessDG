/*
 * 功能: 资产仓库开通的 Noop 实现，未启用 Gitea 时生成确定性仓库引用。
 * 时间: 2026-06-29
 * 作者: AxeXie
 */
package com.aihub.integration.gitea.infrastructure;

import com.aihub.asset.domain.AssetRepositoryProvisioner;
import com.aihub.asset.domain.AssetRepositoryRef;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * Noop 资产仓库开通器。
 *
 * <p>仅当显式配置 {@code aihub.gitea.enabled=false} 时启用；默认可部署环境须接通 Gitea
 * （Compose 设置 {@code AIHUB_GITEA_ENABLED=true}）。按 {@code namespace/name} 生成确定性仓库引用，
 * 供本地单元测试或显式禁用 Gitea 时使用。生产路径由 {@link GiteaAssetRepositoryProvisioner} 承载。
 */
@Component
@ConditionalOnProperty(name = "aihub.gitea.enabled", havingValue = "false", matchIfMissing = false)
public class NoopAssetRepositoryProvisioner implements AssetRepositoryProvisioner {

    @Override
    public AssetRepositoryRef provision(ProvisionRequest request) {
        String fullName = request.namespace() + "/" + request.name();
        return new AssetRepositoryRef(fullName, null, null);
    }
}
