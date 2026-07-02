/*
 * 功能: 统一配置领域聚合，对应 system_config 一行。
 * 时间: 2026-06-30
 * 作者: AxeXie
 */
package com.aihub.configuration.domain;

/**
 * 统一配置聚合。
 *
 * @param configId       业务配置 ID
 * @param configKey      配置键（全局唯一）
 * @param valueType      值类型
 * @param configValue    当前值（文本序列化，为空回退默认值）
 * @param defaultValue   默认值（文本序列化）
 * @param scopeType      作用域类型
 * @param scopeId        作用域 ID
 * @param hotReloadable  是否可热更新（false 需重启生效）
 * @param validator      校验器描述/正则（可空）
 * @param description    说明
 * @param version        语义版本号
 * @param updatedBy      最近更新者主体 ID
 */
public record SystemConfig(String configId,
                           String configKey,
                           ConfigValueType valueType,
                           String configValue,
                           String defaultValue,
                           String scopeType,
                           String scopeId,
                           boolean hotReloadable,
                           String validator,
                           String description,
                           long version,
                           String updatedBy) {
}
