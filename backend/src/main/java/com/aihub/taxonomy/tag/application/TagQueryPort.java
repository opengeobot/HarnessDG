/*
 * 功能: 受控标签查询端口，供资产模块（Task 14）按 tagIds 解析标签视图（含停用，用于回显）。
 * 时间: 2026-06-30
 * 作者: AxeXie
 */
package com.aihub.taxonomy.tag.application;

import com.aihub.taxonomy.tag.application.TagDtos.TagView;
import java.util.Collection;
import java.util.List;

/**
 * 受控标签查询端口。
 *
 * <p>供资产模块查询/回显已关联标签。{@link #viewByIds} 含停用标签，用于历史资产回显。
 * 新建关联的合法性校验由 {@link TagValidationService#resolveActiveTags} 承载。
 */
public interface TagQueryPort {

    /**
     * 按 tagIds 返回标签视图（含停用，用于回显）；未登记的 tagId 静默忽略（回显不抛错）。
     *
     * @param tagIds 标签业务 ID 集合
     * @return 标签视图列表（保持入参顺序尽量一致）
     */
    List<TagView> viewByIds(Collection<String> tagIds);
}
