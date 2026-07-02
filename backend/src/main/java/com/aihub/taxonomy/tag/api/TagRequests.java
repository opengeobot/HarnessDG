/*
 * 功能: 受控标签 API 请求体集合，字段名与 OpenAPI 契约 schema 对齐。
 * 时间: 2026-06-30
 * 作者: AxeXie
 */
package com.aihub.taxonomy.tag.api;

import com.aihub.taxonomy.tag.domain.TagScopeType;

/**
 * 受控标签 API 请求体集合。
 *
 * <p>仅承载入参，不暴露领域对象；字段名与 OpenAPI 契约对齐。
 */
public final class TagRequests {

    private TagRequests() {
    }

    /** 创建标签请求（CreateTagRequest）。 */
    public record CreateTagRequest(TagScopeType scopeType, String scopeId, String tagCode,
                                   String displayName, String i18nKey, String color) {
    }

    /** 更新标签请求（UpdateTagRequest）。 */
    public record UpdateTagRequest(String displayName, String i18nKey, String color, Long expectedVersion) {
    }

    /** 启用/停用请求（携带 expectedVersion 做乐观并发校验，可空）。 */
    public record TagStatusRequest(Long expectedVersion) {
    }
}
