/*
 * 功能: 首个管理员引导配置属性，承载启动期管理员用户名与口令来源（环境变量/Secret 文件）。
 * 时间: 2026-06-30
 * 作者: AxeXie
 */
package com.aihub.identity.infrastructure;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 首个管理员引导配置属性，绑定前缀 {@code aihub.bootstrap.admin}。
 *
 * <p>口令优先从 {@code passwordFile}（Secret 文件路径）读取，其次从 {@code password}（环境变量）读取。
 * 口令绝不硬编码、不写日志、不进 Git；未配置时不创建管理员，仅打印 INFO 提示。
 *
 * @param username     管理员用户名（留空则不引导）
 * @param displayName  管理员展示名
 * @param password     管理员初始口令（环境变量来源，可空）
 * @param passwordFile 管理员初始口令文件路径（Secret 文件来源，可空，优先于 password）
 */
@ConfigurationProperties(prefix = "aihub.bootstrap.admin")
public record BootstrapAdminProperties(String username,
                                       String displayName,
                                       String password,
                                       String passwordFile) {

    public BootstrapAdminProperties {
        displayName = (displayName == null || displayName.isBlank()) ? "平台管理员" : displayName;
    }
}
