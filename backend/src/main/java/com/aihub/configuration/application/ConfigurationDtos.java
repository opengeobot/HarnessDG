/*
 * 功能: 配置应用层视图与命令对象集合，字段名与 OpenAPI 契约 schema 对齐。
 * 时间: 2026-06-30
 * 作者: AxeXie
 */
package com.aihub.configuration.application;

import com.aihub.configuration.domain.ConfigValueType;
import com.aihub.configuration.domain.SystemConfig;

/**
 * 配置应用层 DTO 集合。
 *
 * <p>字段名与 OpenAPI 契约 {@code ConfigurationView}/{@code UpdateConfigurationRequest} 对齐。
 * {@code value} 按值类型暴露为类型安全对象（INTEGER→Integer、BOOLEAN→Boolean 等）。
 * 不可热更新项 {@code hotReloadable=false}，客户端据此提示需重启。
 */
public final class ConfigurationDtos {

    private ConfigurationDtos() {
    }

    /** 配置视图（ConfigurationView）。 */
    public record ConfigView(String configKey, ConfigValueType valueType, Object value,
                             boolean hotReloadable, long version) {
        public static ConfigView from(SystemConfig config, Object typedValue) {
            return new ConfigView(config.configKey(), config.valueType(), typedValue,
                    config.hotReloadable(), config.version());
        }
    }

    /** 更新配置命令（UpdateConfigurationRequest）。 */
    public record UpdateConfigCommand(Object value, long expectedVersion, String confirmation) {
    }
}
