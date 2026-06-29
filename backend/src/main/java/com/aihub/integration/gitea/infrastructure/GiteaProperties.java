/*
 * 功能: Gitea 集成配置属性，绑定部署期 Endpoint、令牌与功能开关。
 * 时间: 2026-06-29
 * 作者: AxeXie
 */
package com.aihub.integration.gitea.infrastructure;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Gitea 集成配置（前缀 {@code aihub.gitea}）。
 *
 * <p>令牌等敏感信息来自环境变量/Secret，不落库、不入日志。{@code enabled} 默认关闭，便于在无 Gitea
 * 的开发/测试环境使用 Noop 仓库开通器；Compose 环境置为 {@code true} 接通真实 Gitea。
 *
 * @param enabled    是否启用真实 Gitea 仓库开通
 * @param baseUrl    Gitea 基础地址（如 http://gitea:3000）
 * @param token      服务账号令牌（敏感，禁止记录）
 * @param defaultBranch 仓库默认分支
 */
@ConfigurationProperties(prefix = "aihub.gitea")
public record GiteaProperties(boolean enabled, String baseUrl, String token, String defaultBranch) {

    public GiteaProperties {
        if (defaultBranch == null || defaultBranch.isBlank()) {
            defaultBranch = "main";
        }
    }
}
