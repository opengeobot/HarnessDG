/*
 * 功能: 受控标签应用层视图与命令对象集合，对外只暴露视图，绝不返回持久化实体。
 * 时间: 2026-06-30
 * 作者: AxeXie
 */
package com.aihub.taxonomy.tag.application;

import com.aihub.taxonomy.domain.TaxonomyStatus;
import com.aihub.taxonomy.tag.domain.Tag;
import com.aihub.taxonomy.tag.domain.TagScopeType;

/**
 * 受控标签应用层 DTO 集合。
 *
 * <p>字段名与 OpenAPI 契约 schema 对齐。视图由领域对象转换得到，不外泄持久化实体。
 */
public final class TagDtos {

    private TagDtos() {
    }

    /** 标签视图（TagView）。 */
    public record TagView(String tagId, TagScopeType scopeType, String scopeId, String tagCode,
                          String displayName, String i18nKey, String color,
                          TaxonomyStatus status, long version) {
        public static TagView from(Tag tag) {
            return new TagView(tag.tagId(), tag.scopeType(), tag.scopeId(), tag.tagCode(),
                    tag.name(), tag.i18nKey(), tag.color(), tag.status(), tag.version());
        }
    }

    /** 创建标签命令（CreateTagRequest）。 */
    public record CreateTagCommand(TagScopeType scopeType, String scopeId, String tagCode,
                                   String displayName, String i18nKey, String color) {
    }

    /** 更新标签命令（UpdateTagRequest）。 */
    public record UpdateTagCommand(String displayName, String i18nKey, String color, Long expectedVersion) {
    }

    /** 标签作用域上下文，用于 resolveActiveTags 判定组织作用域标签的可引用性。 */
    public record TagScopeContext(TagScopeType scopeType, String scopeId) {

        /** 平台作用域上下文（可引用全部标签）。 */
        public static TagScopeContext platform() {
            return new TagScopeContext(TagScopeType.PLATFORM, "PLATFORM");
        }
    }
}
