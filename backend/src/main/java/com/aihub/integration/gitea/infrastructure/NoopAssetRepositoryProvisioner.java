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
 * <p>当 {@code aihub.gitea.enabled=false}（默认）时启用：不调用外部系统，按 {@code namespace/name}
 * 生成确定性仓库引用，使资产目录在无 Gitea 的开发/测试环境完整可用。接通 Gitea 后由
 * {@link GiteaAssetRepositoryProvisioner} 替代。
 */
@Component
@ConditionalOnProperty(name = "aihub.gitea.enabled", havingValue = "false", matchIfMissing = true)
public class NoopAssetRepositoryProvisioner implements AssetRepositoryProvisioner {

    @Override
    public AssetRepositoryRef provision(ProvisionRequest request) {
        String fullName = request.namespace() + "/" + request.name();
        return new AssetRepositoryRef(fullName, null, null);
    }
}
