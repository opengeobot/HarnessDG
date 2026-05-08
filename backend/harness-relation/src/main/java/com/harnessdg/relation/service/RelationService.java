package com.harnessdg.relation.service;

import com.harnessdg.model.relation.dto.*;

import java.util.List;

/**
 * 功能：实体关系服务接口
 * 时间：2026-05-08
 * 作者：AxeXie
 */
public interface RelationService {

    /**
     * 查询所有关系，支持按源实体、目标实体、关系类型过滤
     */
    List<RelationDTO> listRelations(Long sourceEntityId, Long targetEntityId, String relationType, String locale);

    /**
     * 查询某实体的所有关联关系
     * direction: "outgoing"(作为源), "incoming"(作为目标), "both"(双向)
     */
    List<RelationDTO> getRelationsByEntity(Long entityId, String direction, String locale);

    /**
     * 创建关系
     */
    RelationDTO createRelation(RelationCreateRequest request);

    /**
     * 更新关系
     */
    RelationDTO updateRelation(Long id, RelationUpdateRequest request);

    /**
     * 删除关系
     */
    void deleteRelation(Long id);
}
