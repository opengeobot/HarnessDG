/*
 * 功能: 受控标签领域聚合，对应 system_tag 一行。
 * 时间: 2026-06-30
 * 作者: AxeXie
 */
package com.aihub.taxonomy.tag.domain;

import com.aihub.taxonomy.domain.TaxonomyStatus;

/**
 * 受控标签聚合。
 *
 * @param tagId      业务标签 ID（tag_）
 * @param scopeType  作用域类型（PLATFORM/ORGANIZATION）
 * @param scopeId    作用域 ID（平台标签为 PLATFORM；组织标签为 organization_id）
 * @param tagCode    标签编码（作用域内稳定值）
 * @param name       标签展示名（兜底展示）
 * @param i18nKey    国际化键
 * @param color      标签颜色（可空）
 * @param status     状态（ACTIVE 可新建关联 / DISABLED 仅回显）
 * @param createdBy  创建者主体 ID
 * @param version    语义版本号
 */
public record Tag(String tagId,
                  TagScopeType scopeType,
                  String scopeId,
                  String tagCode,
                  String name,
                  String i18nKey,
                  String color,
                  TaxonomyStatus status,
                  String createdBy,
                  long version) {
}
