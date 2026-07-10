/*
 * 功能: 预览模块配置注册。
 * 时间: 2026-07-10
 * 作者: AxeXie
 */
package com.aihub.version.infrastructure;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties(PreviewProperties.class)
public class PreviewConfiguration {
}
