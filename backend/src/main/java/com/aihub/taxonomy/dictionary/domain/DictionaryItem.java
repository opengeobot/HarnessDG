/*
 * 功能: 字典项领域聚合，对应 system_dict_item 一行（只存 item_code + i18n_key，不存文案）。
 * 时间: 2026-06-30
 * 作者: AxeXie
 */
package com.aihub.taxonomy.dictionary.domain;

import com.aihub.taxonomy.domain.TaxonomyStatus;

/**
 * 字典项聚合。
 *
 * <p>仅承载稳定的 {@code itemCode} 与 {@code i18nKey}，不存展示文案；治理字段持久化 itemCode。
 *
 * @param dictItemId 业务字典项 ID
 * @param dictCode   所属字典编码
 * @param itemCode   字典项编码（治理字段实际持久化值）
 * @param i18nKey    国际化键
 * @param sortOrder  排序序号
 * @param status     状态（ACTIVE 可新建引用 / DISABLED 仅回显）
 * @param version    语义版本号
 */
public record DictionaryItem(String dictItemId,
                             String dictCode,
                             String itemCode,
                             String i18nKey,
                             int sortOrder,
                             TaxonomyStatus status,
                             long version) {
}
