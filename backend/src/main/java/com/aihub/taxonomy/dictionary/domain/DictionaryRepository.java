/*
 * 功能: 字典仓储端口，约定字典类型与字典项的持久化与查询（含缓存版本自增）。
 * 时间: 2026-06-30
 * 作者: AxeXie
 */
package com.aihub.taxonomy.dictionary.domain;

import java.util.List;
import java.util.Optional;

/**
 * 字典仓储端口。
 *
 * <p>领域层只依赖本端口；实现位于 infrastructure。字典项新增/修改/停用时由 {@link #incrementTypeVersion}
 * 自增所属字典类型的缓存版本，供前端/查询投影缓存失效。
 */
public interface DictionaryRepository {

    /** 列出全部字典类型（按编码排序）。 */
    List<DictionaryType> findAllTypes();

    /** @return 字典类型是否存在（含停用）。 */
    boolean typeExists(String dictCode);

    /**
     * 列出字典项；{@code includeDisabled=false} 时仅返回 ACTIVE 项（用于新建引用选择），
     * {@code true} 时含停用项（用于历史回显）。
     */
    List<DictionaryItem> findItems(String dictCode, boolean includeDisabled);

    /** 按字典编码与项编码查找字典项（含停用，用于回显）。 */
    Optional<DictionaryItem> findItem(String dictCode, String itemCode);

    /** 持久化新字典项。 */
    void insertItem(DictionaryItem item);

    /** 更新字典项（i18nKey/sortOrder/status），并以 expectedVersion 做乐观并发校验。 */
    void updateItem(DictionaryItem item, long expectedVersion);

    /** 自增字典类型缓存版本号（字典项变更时调用）。 */
    void incrementTypeVersion(String dictCode);
}
