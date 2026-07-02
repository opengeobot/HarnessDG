/*
 * 功能: taxonomy 字典应用层视图与命令对象集合，对外只暴露视图，绝不返回持久化实体。
 * 时间: 2026-06-30
 * 作者: AxeXie
 */
package com.aihub.taxonomy.dictionary.application;

import com.aihub.taxonomy.dictionary.domain.DictionaryItem;
import com.aihub.taxonomy.dictionary.domain.DictionaryType;
import com.aihub.taxonomy.domain.TaxonomyStatus;

/**
 * 字典应用层 DTO 集合。
 *
 * <p>承载用例输入命令与对外视图；字段名与 OpenAPI 契约 schema 对齐。视图由领域对象转换得到，
 * 不外泄持久化实体。
 */
public final class DictionaryDtos {

    private DictionaryDtos() {
    }

    /** 字典类型视图（DictionaryTypeView）。 */
    public record DictionaryTypeView(String dictCode, String i18nKey, long version, TaxonomyStatus status) {
        public static DictionaryTypeView from(DictionaryType type) {
            return new DictionaryTypeView(type.dictCode(), type.i18nKey(), type.version(), type.status());
        }
    }

    /** 字典项视图（DictionaryItemView）。 */
    public record DictionaryItemView(String dictCode, String itemCode, String i18nKey,
                                     int sortOrder, TaxonomyStatus status, long version) {
        public static DictionaryItemView from(DictionaryItem item) {
            return new DictionaryItemView(item.dictCode(), item.itemCode(), item.i18nKey(),
                    item.sortOrder(), item.status(), item.version());
        }
    }

    /** 新建字典项命令（CreateDictionaryItemRequest）。 */
    public record CreateDictionaryItemCommand(String itemCode, String i18nKey, Integer sortOrder) {
    }

    /** 更新字典项命令（UpdateDictionaryItemRequest）。 */
    public record UpdateDictionaryItemCommand(String i18nKey, Integer sortOrder,
                                              TaxonomyStatus status, Long expectedVersion) {
    }
}
