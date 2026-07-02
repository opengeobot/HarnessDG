/*
 * 功能: 字典 API 请求体集合，字段名与 OpenAPI 契约 schema 对齐。
 * 时间: 2026-06-30
 * 作者: AxeXie
 */
package com.aihub.taxonomy.dictionary.api;

import com.aihub.taxonomy.domain.TaxonomyStatus;

/**
 * 字典 API 请求体集合。
 *
 * <p>仅承载入参，不暴露领域对象；字段名与 OpenAPI 契约对齐。
 */
public final class DictionaryRequests {

    private DictionaryRequests() {
    }

    /** 新增字典项请求（CreateDictionaryItemRequest）。 */
    public record CreateDictionaryItemRequest(String itemCode, String i18nKey, Integer sortOrder) {
    }

    /** 更新字典项请求（UpdateDictionaryItemRequest）。 */
    public record UpdateDictionaryItemRequest(String i18nKey, Integer sortOrder,
                                              TaxonomyStatus status, Long expectedVersion) {
    }
}
