/*
 * 功能: Gitea 集成装配，注册配置属性绑定。
 * 时间: 2026-06-29
 * 作者: AxeXie
 */
package com.aihub.integration.gitea.infrastructure;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 * Gitea 集成装配。绑定 {@link GiteaProperties}，供仓库开通器按开关选择实现。
 */
@Configuration
@EnableConfigurationProperties(GiteaProperties.class)
public class GiteaConfiguration {
}
