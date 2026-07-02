/*
 * 功能: 受控标签仓储端口，约定标签的持久化与查询（含作用域唯一性判定与按 ID 批量解析）。
 * 时间: 2026-06-30
 * 作者: AxeXie
 */
package com.aihub.taxonomy.tag.domain;

import java.util.List;
import java.util.Optional;

/**
 * 受控标签仓储端口。
 *
 * <p>领域层只依赖本端口；实现位于 infrastructure。作用域唯一性由数据库唯一约束保证，
 * 仓储额外提供存在性判定供应用层在创建前给出友好冲突错误。
 */
public interface TagRepository {

    /**
     * 列出标签（可按作用域/状态/关键字过滤）。
     *
     * @param scopeType 作用域类型过滤（可空）
     * @param scopeId   作用域 ID 过滤（可空）
     * @param status    状态过滤（可空）
     * @param keyword   名称/编码关键字模糊匹配（可空）
     */
    List<Tag> search(TagScopeType scopeType, String scopeId,
                     com.aihub.taxonomy.domain.TaxonomyStatus status, String keyword);

    /** 按 tagId 查找（含停用，用于回显）。 */
    Optional<Tag> findByTagId(String tagId);

    /** 按 tagIds 批量查找（含停用，用于回显）。 */
    List<Tag> findByTagIds(List<String> tagIds);

    /** 作用域内 (scopeType, scopeId, tagCode) 是否已存在。 */
    boolean existsByCode(TagScopeType scopeType, String scopeId, String tagCode);

    /** 持久化新标签。 */
    void insert(Tag tag);

    /** 更新标签（name/i18nKey/color），以 expectedVersion 做乐观并发校验。 */
    void update(Tag tag, long expectedVersion);

    /** 更新标签状态（启用/停用），以 expectedVersion 做乐观并发校验。 */
    void updateStatus(String tagId, com.aihub.taxonomy.domain.TaxonomyStatus status, long expectedVersion);
}
