/*
 * 功能: 受控标签校验服务，供资产模块（Task 14）在写入 tagIds 时校验全部存在、ACTIVE 且作用域允许，
 *       拒绝自由标签/未登记 tagId；并实现 TagQueryPort 提供含停用标签的回显查询。
 * 时间: 2026-06-30
 * 作者: AxeXie
 */
package com.aihub.taxonomy.tag.application;

import com.aihub.shared.error.ErrorCode;
import com.aihub.shared.error.ValidationException;
import com.aihub.taxonomy.domain.TaxonomyStatus;
import com.aihub.taxonomy.tag.application.TagDtos.TagScopeContext;
import com.aihub.taxonomy.tag.application.TagDtos.TagView;
import com.aihub.taxonomy.tag.domain.Tag;
import com.aihub.taxonomy.tag.domain.TagRepository;
import com.aihub.taxonomy.tag.domain.TagScopeType;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 受控标签校验服务。
 *
 * <p>实现 {@link TagQueryPort}：
 * <ul>
 *   <li>{@link #resolveActiveTags}：校验一组 tagId 全部存在且 ACTIVE 且作用域允许，否则抛
 *       {@link ValidationException}（错误码 {@link ErrorCode#TAG_VALUE_INVALID}）。<b>拒绝自由标签
 *       与未登记 tagId</b>。停用标签不可用于新建关联。</li>
 *   <li>{@link #viewByIds}：含停用标签，用于历史资产回显；未登记 tagId 静默忽略（回显不抛错）。</li>
 * </ul>
 */
@Service
public class TagValidationService implements TagQueryPort {

    private final TagRepository repository;

    public TagValidationService(TagRepository repository) {
        this.repository = repository;
    }

    @Override
    @Transactional(readOnly = true)
    public List<TagView> viewByIds(Collection<String> tagIds) {
        if (tagIds == null || tagIds.isEmpty()) {
            return List.of();
        }
        List<String> ids = tagIds.stream().distinct().toList();
        List<Tag> tags = repository.findByTagIds(ids);
        Map<String, Tag> byId = new LinkedHashMap<>();
        for (Tag tag : tags) {
            byId.put(tag.tagId(), tag);
        }
        List<TagView> result = new ArrayList<>(ids.size());
        for (String id : ids) {
            Tag tag = byId.get(id);
            if (tag != null) {
                result.add(TagView.from(tag));
            }
        }
        return result;
    }

    /**
     * 校验一组 tagId 可用于新建关联：必须全部存在、ACTIVE 且作用域允许。
     *
     * @param tagIds        待校验标签 ID 集合
     * @param scopeContext  资产作用域上下文（PLATFORM 可引用全部；ORGANIZATION 仅可引用 PLATFORM 标签与本组织标签）
     * @return 通过校验的标签视图列表（保持入参顺序）
     */
    @Transactional(readOnly = true)
    public List<TagView> resolveActiveTags(Collection<String> tagIds, TagScopeContext scopeContext) {
        if (tagIds == null || tagIds.isEmpty()) {
            return List.of();
        }
        if (scopeContext == null) {
            throw new ValidationException("scopeContext is required");
        }
        List<String> ids = tagIds.stream().distinct().toList();
        List<Tag> tags = repository.findByTagIds(ids);
        Map<String, Tag> byId = new LinkedHashMap<>();
        for (Tag tag : tags) {
            byId.put(tag.tagId(), tag);
        }
        List<TagView> result = new ArrayList<>(ids.size());
        for (String id : ids) {
            Tag tag = byId.get(id);
            if (tag == null) {
                throw new ValidationException(ErrorCode.TAG_VALUE_INVALID,
                        "tag is not registered: " + id, Map.of("tagId", id));
            }
            if (tag.status() != TaxonomyStatus.ACTIVE) {
                throw new ValidationException(ErrorCode.TAG_VALUE_INVALID,
                        "tag is disabled and cannot be used for new associations: " + id,
                        Map.of("tagId", id, "status", tag.status().name()));
            }
            if (!isScopeAllowed(tag, scopeContext)) {
                throw new ValidationException(ErrorCode.TAG_VALUE_INVALID,
                        "tag scope is not allowed in this context: " + id,
                        Map.of("tagId", id, "tagScope", tag.scopeType().name(),
                                "tagScopeId", tag.scopeId()));
            }
            result.add(TagView.from(tag));
        }
        return result;
    }

    /** 平台作用域上下文可引用全部标签；组织作用域上下文仅可引用 PLATFORM 标签与本组织标签。 */
    private boolean isScopeAllowed(Tag tag, TagScopeContext scopeContext) {
        if (scopeContext.scopeType() == TagScopeType.PLATFORM) {
            return true;
        }
        if (tag.scopeType() == TagScopeType.PLATFORM) {
            return true;
        }
        return tag.scopeId().equals(scopeContext.scopeId());
    }
}
