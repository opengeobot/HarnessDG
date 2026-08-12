package com.modelhub.catalog.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 * catalog 模块装配：运行参数绑定（Gitea 连接、轮询/重试策略）。
 * 调度开关由 app 启动类统一 @EnableScheduling。
 */
@Configuration
@EnableConfigurationProperties({GiteaProperties.class, CatalogProperties.class})
public class CatalogConfiguration {
}
