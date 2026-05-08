package com.harnessdg.relation.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.harnessdg.common.exception.BizException;
import com.harnessdg.common.i18n.I18nTextUtils;
import com.harnessdg.common.response.ErrorCode;
import com.harnessdg.model.ontology.entity.OntEntity;
import com.harnessdg.model.ontology.entity.OntRelation;
import com.harnessdg.model.relation.dto.*;
import com.harnessdg.ontology.mapper.OntEntityMapper;
import com.harnessdg.relation.mapper.OntRelationMapper;
import com.harnessdg.relation.service.RelationService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;

/**
 * 功能：实体关系服务实现
 * 时间：2026-05-08
 * 作者：AxeXie
 */
@Service
@RequiredArgsConstructor
public class RelationServiceImpl implements RelationService {

    private final OntRelationMapper relationMapper;
    private final OntEntityMapper entityMapper;

    @Override
    public List<RelationDTO> listRelations(Long sourceEntityId, Long targetEntityId,
                                            String relationType, String locale) {
        LambdaQueryWrapper<OntRelation> wrapper = new LambdaQueryWrapper<>();
        if (sourceEntityId != null) {
            wrapper.eq(OntRelation::getSourceEntityId, sourceEntityId);
        }
        if (targetEntityId != null) {
            wrapper.eq(OntRelation::getTargetEntityId, targetEntityId);
        }
        if (relationType != null && !relationType.isBlank()) {
            wrapper.eq(OntRelation::getRelationType, relationType);
        }
        wrapper.orderByAsc(OntRelation::getRelationType);

        List<OntRelation> relations = relationMapper.selectList(wrapper);
        return relations.stream()
                .map(r -> toDTO(r, locale))
                .toList();
    }

    @Override
    public List<RelationDTO> getRelationsByEntity(Long entityId, String direction, String locale) {
        LambdaQueryWrapper<OntRelation> wrapper = new LambdaQueryWrapper<>();

        if ("outgoing".equals(direction)) {
            wrapper.eq(OntRelation::getSourceEntityId, entityId);
        } else if ("incoming".equals(direction)) {
            wrapper.eq(OntRelation::getTargetEntityId, entityId);
        } else {
            wrapper.and(w -> w
                    .eq(OntRelation::getSourceEntityId, entityId)
                    .or()
                    .eq(OntRelation::getTargetEntityId, entityId));
        }
        wrapper.orderByAsc(OntRelation::getRelationType);

        List<OntRelation> relations = relationMapper.selectList(wrapper);
        return relations.stream()
                .map(r -> toDTO(r, locale))
                .toList();
    }

    @Override
    @Transactional
    public RelationDTO createRelation(RelationCreateRequest request) {
        // 验证源实体和目标实体存在
        if (entityMapper.selectById(request.getSourceEntityId()) == null) {
            throw new BizException(ErrorCode.ENTITY_NOT_FOUND);
        }
        if (entityMapper.selectById(request.getTargetEntityId()) == null) {
            throw new BizException(ErrorCode.ENTITY_NOT_FOUND);
        }

        OntRelation relation = new OntRelation();
        relation.setSourceEntityId(request.getSourceEntityId());
        relation.setTargetEntityId(request.getTargetEntityId());
        relation.setRelationType(request.getRelationType());
        relation.setName(request.getName());
        relation.setDescription(request.getDescription());
        relation.setCardinality(request.getCardinality());
        relation.setExtra(request.getExtra());
        relation.setStatus("active");

        relationMapper.insert(relation);
        return toDTO(relation, null);
    }

    @Override
    @Transactional
    public RelationDTO updateRelation(Long id, RelationUpdateRequest request) {
        OntRelation relation = relationMapper.selectById(id);
        if (relation == null) {
            throw new BizException(ErrorCode.RELATION_NOT_FOUND);
        }

        relation.setRelationType(request.getRelationType());
        if (request.getName() != null) {
            relation.setName(request.getName());
        }
        if (request.getDescription() != null) {
            relation.setDescription(request.getDescription());
        }
        relation.setCardinality(request.getCardinality());
        if (request.getExtra() != null) {
            relation.setExtra(request.getExtra());
        }
        if (request.getStatus() != null) {
            relation.setStatus(request.getStatus());
        }

        relationMapper.updateById(relation);
        return toDTO(relation, null);
    }

    @Override
    @Transactional
    public void deleteRelation(Long id) {
        if (relationMapper.selectById(id) == null) {
            throw new BizException(ErrorCode.RELATION_NOT_FOUND);
        }
        relationMapper.deleteById(id);
    }

    // === Converters ===

    private RelationDTO toDTO(OntRelation r, String locale) {
        RelationDTO dto = new RelationDTO();
        dto.setId(r.getId());
        dto.setSourceEntityId(r.getSourceEntityId());
        dto.setTargetEntityId(r.getTargetEntityId());
        dto.setRelationType(r.getRelationType());
        dto.setName(r.getName());
        dto.setDescription(r.getDescription());
        dto.setCardinality(r.getCardinality());
        dto.setExtra(r.getExtra());
        dto.setStatus(r.getStatus());

        // 解析实体编码
        OntEntity source = entityMapper.selectById(r.getSourceEntityId());
        if (source != null) {
            dto.setSourceEntityCode(source.getCode());
        }
        OntEntity target = entityMapper.selectById(r.getTargetEntityId());
        if (target != null) {
            dto.setTargetEntityCode(target.getCode());
        }

        if (locale != null) {
            dto.setResolvedName(I18nTextUtils.resolve(r.getName(), locale));
        }
        return dto;
    }
}
