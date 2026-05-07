package com.harnessdg.ontology.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.harnessdg.common.exception.BizException;
import com.harnessdg.common.i18n.I18nTextUtils;
import com.harnessdg.common.page.PageRequest;
import com.harnessdg.common.page.PageResult;
import com.harnessdg.common.response.ErrorCode;
import com.harnessdg.model.ontology.dto.*;
import com.harnessdg.model.ontology.entity.OntDimension;
import com.harnessdg.model.ontology.entity.OntEntity;
import com.harnessdg.model.ontology.entity.OntMetric;
import com.harnessdg.ontology.mapper.OntDimensionMapper;
import com.harnessdg.ontology.mapper.OntEntityMapper;
import com.harnessdg.ontology.mapper.OntMetricMapper;
import com.harnessdg.ontology.service.OntologyService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class OntologyServiceImpl implements OntologyService {

    private final OntEntityMapper entityMapper;
    private final OntMetricMapper metricMapper;
    private final OntDimensionMapper dimensionMapper;

    @Override
    public PageResult<OntEntityDTO> listEntities(String domain, String status,
                                                  String keyword, PageRequest pageRequest) {
        LambdaQueryWrapper<OntEntity> wrapper = new LambdaQueryWrapper<>();
        if (domain != null && !domain.isBlank()) {
            wrapper.eq(OntEntity::getDomain, domain);
        }
        if (status != null && !status.isBlank()) {
            wrapper.eq(OntEntity::getStatus, status);
        }
        if (keyword != null && !keyword.isBlank()) {
            wrapper.like(OntEntity::getCode, keyword);
        }
        wrapper.orderByDesc(OntEntity::getUpdatedAt);

        Page<OntEntity> page = new Page<>(pageRequest.getPage(), pageRequest.getPageSize());
        Page<OntEntity> result = entityMapper.selectPage(page, wrapper);

        List<OntEntityDTO> records = result.getRecords().stream()
                .map(this::toEntityDTO)
                .toList();
        return PageResult.of(records, result.getTotal(), pageRequest.getPage(), pageRequest.getPageSize());
    }

    @Override
    public OntEntityDTO getEntityById(Long id, String locale) {
        OntEntity entity = entityMapper.selectById(id);
        if (entity == null) {
            throw new BizException(ErrorCode.ENTITY_NOT_FOUND);
        }
        OntEntityDTO dto = toEntityDTO(entity);
        if (locale != null) {
            dto.setResolvedName(I18nTextUtils.resolve(entity.getName(), locale));
        }
        dto.setMetrics(listMetricsByEntity(id));
        dto.setDimensions(listDimensionsByEntity(id));
        return dto;
    }

    @Override
    @Transactional
    public OntEntityDTO createEntity(EntityCreateRequest request) {
        OntEntity entity = new OntEntity();
        entity.setCode(request.getCode());
        entity.setName(request.getName());
        entity.setDescription(request.getDescription());
        entity.setTableName(request.getTableName());
        entity.setDomain(request.getDomain());
        entity.setOwner(request.getOwner());
        entity.setStatus("draft");
        entity.setTags(request.getTags());
        entityMapper.insert(entity);
        return toEntityDTO(entity);
    }

    @Override
    @Transactional
    public OntEntityDTO updateEntity(Long id, EntityCreateRequest request) {
        OntEntity entity = entityMapper.selectById(id);
        if (entity == null) {
            throw new BizException(ErrorCode.ENTITY_NOT_FOUND);
        }
        entity.setCode(request.getCode());
        entity.setName(request.getName());
        entity.setDescription(request.getDescription());
        entity.setTableName(request.getTableName());
        entity.setDomain(request.getDomain());
        entity.setOwner(request.getOwner());
        entity.setTags(request.getTags());
        entityMapper.updateById(entity);
        return toEntityDTO(entity);
    }

    @Override
    @Transactional
    public void deleteEntity(Long id) {
        if (entityMapper.selectById(id) == null) {
            throw new BizException(ErrorCode.ENTITY_NOT_FOUND);
        }
        entityMapper.deleteById(id);
    }

    @Override
    public List<OntMetricDTO> listMetricsByEntity(Long entityId) {
        LambdaQueryWrapper<OntMetric> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(OntMetric::getEntityId, entityId)
                .orderByAsc(OntMetric::getCode);
        return metricMapper.selectList(wrapper).stream()
                .map(this::toMetricDTO)
                .toList();
    }

    @Override
    @Transactional
    public OntMetricDTO createMetric(MetricCreateRequest request) {
        OntMetric metric = new OntMetric();
        metric.setCode(request.getCode());
        metric.setName(request.getName());
        metric.setDescription(request.getDescription());
        metric.setEntityId(request.getEntityId());
        metric.setMetricType(request.getMetricType());
        metric.setAggMethod(request.getAggMethod());
        metric.setExpression(request.getExpression());
        metric.setUnit(request.getUnit());
        metric.setOwner(request.getOwner());
        metric.setStatus("draft");
        metric.setTags(request.getTags());
        metricMapper.insert(metric);
        return toMetricDTO(metric);
    }

    @Override
    @Transactional
    public OntMetricDTO updateMetric(Long id, MetricCreateRequest request) {
        OntMetric metric = metricMapper.selectById(id);
        if (metric == null) {
            throw new BizException(ErrorCode.METRIC_NOT_FOUND);
        }
        metric.setCode(request.getCode());
        metric.setName(request.getName());
        metric.setDescription(request.getDescription());
        metric.setEntityId(request.getEntityId());
        metric.setMetricType(request.getMetricType());
        metric.setAggMethod(request.getAggMethod());
        metric.setExpression(request.getExpression());
        metric.setUnit(request.getUnit());
        metric.setOwner(request.getOwner());
        metric.setTags(request.getTags());
        metricMapper.updateById(metric);
        return toMetricDTO(metric);
    }

    @Override
    @Transactional
    public void deleteMetric(Long id) {
        if (metricMapper.selectById(id) == null) {
            throw new BizException(ErrorCode.METRIC_NOT_FOUND);
        }
        metricMapper.deleteById(id);
    }

    @Override
    public List<OntDimensionDTO> listDimensionsByEntity(Long entityId) {
        LambdaQueryWrapper<OntDimension> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(OntDimension::getEntityId, entityId)
                .orderByAsc(OntDimension::getCode);
        return dimensionMapper.selectList(wrapper).stream()
                .map(this::toDimensionDTO)
                .toList();
    }

    @Override
    @Transactional
    public OntDimensionDTO createDimension(DimensionCreateRequest request) {
        OntDimension dim = new OntDimension();
        dim.setCode(request.getCode());
        dim.setName(request.getName());
        dim.setDescription(request.getDescription());
        dim.setEntityId(request.getEntityId());
        dim.setDimType(request.getDimType());
        dim.setTableColumn(request.getTableColumn());
        dim.setHierarchyLevel(request.getHierarchyLevel());
        dim.setStatus("draft");
        dim.setTags(request.getTags());
        dimensionMapper.insert(dim);
        return toDimensionDTO(dim);
    }

    @Override
    @Transactional
    public OntDimensionDTO updateDimension(Long id, DimensionCreateRequest request) {
        OntDimension dim = dimensionMapper.selectById(id);
        if (dim == null) {
            throw new BizException(ErrorCode.DIMENSION_NOT_FOUND);
        }
        dim.setCode(request.getCode());
        dim.setName(request.getName());
        dim.setDescription(request.getDescription());
        dim.setEntityId(request.getEntityId());
        dim.setDimType(request.getDimType());
        dim.setTableColumn(request.getTableColumn());
        dim.setHierarchyLevel(request.getHierarchyLevel());
        dim.setTags(request.getTags());
        dimensionMapper.updateById(dim);
        return toDimensionDTO(dim);
    }

    @Override
    @Transactional
    public void deleteDimension(Long id) {
        if (dimensionMapper.selectById(id) == null) {
            throw new BizException(ErrorCode.DIMENSION_NOT_FOUND);
        }
        dimensionMapper.deleteById(id);
    }

    // === Converters ===

    private OntEntityDTO toEntityDTO(OntEntity e) {
        OntEntityDTO dto = new OntEntityDTO();
        dto.setId(e.getId());
        dto.setCode(e.getCode());
        dto.setName(e.getName());
        dto.setDescription(e.getDescription());
        dto.setTableName(e.getTableName());
        dto.setDomain(e.getDomain());
        dto.setOwner(e.getOwner());
        dto.setStatus(e.getStatus());
        dto.setTags(e.getTags());
        return dto;
    }

    private OntMetricDTO toMetricDTO(OntMetric m) {
        OntMetricDTO dto = new OntMetricDTO();
        dto.setId(m.getId());
        dto.setCode(m.getCode());
        dto.setName(m.getName());
        dto.setDescription(m.getDescription());
        dto.setEntityId(m.getEntityId());
        dto.setMetricType(m.getMetricType());
        dto.setAggMethod(m.getAggMethod());
        dto.setExpression(m.getExpression());
        dto.setUnit(m.getUnit());
        dto.setOwner(m.getOwner());
        dto.setStatus(m.getStatus());
        dto.setTags(m.getTags());
        return dto;
    }

    private OntDimensionDTO toDimensionDTO(OntDimension d) {
        OntDimensionDTO dto = new OntDimensionDTO();
        dto.setId(d.getId());
        dto.setCode(d.getCode());
        dto.setName(d.getName());
        dto.setDescription(d.getDescription());
        dto.setEntityId(d.getEntityId());
        dto.setDimType(d.getDimType());
        dto.setTableColumn(d.getTableColumn());
        dto.setHierarchyLevel(d.getHierarchyLevel());
        dto.setStatus(d.getStatus());
        dto.setTags(d.getTags());
        return dto;
    }
}
