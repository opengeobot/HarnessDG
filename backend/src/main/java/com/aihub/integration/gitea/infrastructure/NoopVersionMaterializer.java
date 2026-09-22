/*
 * 功能: 本地/Noop 版本物化适配器——Gitea 禁用时由调用方设置 synthetic sourceCommit。
 * 时间: 2026-07-10
 * 作者: AxeXie
 */
package com.aihub.integration.gitea.infrastructure;

import com.aihub.version.domain.VersionMaterializationPort;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * Gitea 未启用时的版本物化回退。
 */
@Component
@ConditionalOnProperty(name = "aihub.gitea.enabled", havingValue = "false", matchIfMissing = true)
public class NoopVersionMaterializer implements VersionMaterializationPort {

    private static final Logger LOG = LoggerFactory.getLogger(NoopVersionMaterializer.class);

    @Override
    public MaterializationResult materialize(MaterializationRequest request) {
        LOG.info("gitea disabled, using local synthetic sourceCommit for repo={}", request.repoFullName());
        return new MaterializationResult(null, false,
                "gitea disabled; caller should set local-{manifestDigest} sourceCommit");
    }
}
