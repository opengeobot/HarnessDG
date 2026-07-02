/*
 * 功能: 配置 API 请求体集合，字段名与 OpenAPI 契约 schema 对齐。
 * 时间: 2026-06-30
 * 作者: AxeXie
 */
package com.aihub.configuration.api;

/**
 * 配置 API 请求体集合。
 *
 * <p>仅承载入参，不暴露领域对象；字段名与 OpenAPI 契约 {@code UpdateConfigurationRequest} 对齐。
 */
public final class ConfigurationRequests {

    private ConfigurationRequests() {
    }

    /** 更新配置请求（UpdateConfigurationRequest）。 */
    public record UpdateConfigurationRequest(Object value, long expectedVersion, String confirmation) {
    }
}
