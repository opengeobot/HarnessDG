/*
 * 功能: 可部署环境 Gitea 开通器门禁测试——Noop 不得默认可用。
 * 时间: 2026-07-10
 * 作者: AxeXie
 */
package com.aihub.integration.gitea.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;

/**
 * Compose 可部署路径要求 {@code AIHUB_GITEA_ENABLED=true}；Noop 仅在显式 {@code false} 时注册。
 */
class DeployableProvisionerProfileTest {

    @Test
    void noopProvisionerOnlyWhenGiteaExplicitlyDisabled() {
        ConditionalOnProperty annotation = NoopAssetRepositoryProvisioner.class
                .getAnnotation(ConditionalOnProperty.class);
        assertThat(annotation).isNotNull();
        assertThat(annotation.name()).containsExactly("aihub.gitea.enabled");
        assertThat(annotation.havingValue()).isEqualTo("false");
        assertThat(annotation.matchIfMissing()).isFalse();
    }
}
