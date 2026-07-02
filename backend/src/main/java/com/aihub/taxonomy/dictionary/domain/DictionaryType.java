/*
 * 功能: 字典类型领域聚合，对应 system_dict_type 一行。
 * 时间: 2026-06-30
 * 作者: AxeXie
 */
package com.aihub.taxonomy.dictionary.domain;

import com.aihub.taxonomy.domain.TaxonomyStatus;

/**
 * 字典类型聚合。
 *
 * @param dictTypeId  业务字典类型 ID
 * @param dictCode    字典编码（全局唯一）
 * @param name        字典类型名称
 * @param i18nKey     国际化键
 * @param description 说明
 * @param status      状态
 * @param version     缓存版本号（字典项变更时自增）
 */
public record DictionaryType(String dictTypeId,
                             String dictCode,
                             String name,
                             String i18nKey,
                             String description,
                             TaxonomyStatus status,
                             long version) {
}
