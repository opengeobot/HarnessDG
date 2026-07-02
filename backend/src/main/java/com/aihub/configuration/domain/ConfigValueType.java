/*
 * 功能: 配置值类型枚举，对应 system_config.value_type 列，用于类型安全读取与提交校验。
 * 时间: 2026-06-30
 * 作者: AxeXie
 */
package com.aihub.configuration.domain;

/**
 * 配置值类型。
 *
 * <p>与 OpenAPI {@code ConfigurationView.valueType} 对齐。类型安全读取
 * （{@link com.aihub.configuration.application.PlatformConfigService}）按本类型解析存储文本。
 */
public enum ConfigValueType {

    /** 字符串。 */
    STRING,
    /** 整数（int）。 */
    INTEGER,
    /** 长整数（long）。 */
    LONG,
    /** 布尔（true/false）。 */
    BOOLEAN,
    /** 时长（ISO-8601，如 PT5M）。 */
    DURATION,
    /** JSON 文本。 */
    JSON
}
