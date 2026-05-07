package com.harnessdg.ontology.service;

import com.harnessdg.common.page.PageRequest;
import com.harnessdg.common.page.PageResult;
import com.harnessdg.model.ontology.dto.*;

import java.util.List;

public interface OntologyService {

    // Entity CRUD
    PageResult<OntEntityDTO> listEntities(String domain, String status, String keyword, PageRequest pageRequest);

    OntEntityDTO getEntityById(Long id, String locale);

    OntEntityDTO createEntity(EntityCreateRequest request);

    OntEntityDTO updateEntity(Long id, EntityCreateRequest request);

    void deleteEntity(Long id);

    // Metric CRUD
    List<OntMetricDTO> listMetricsByEntity(Long entityId);

    OntMetricDTO createMetric(MetricCreateRequest request);

    OntMetricDTO updateMetric(Long id, MetricCreateRequest request);

    void deleteMetric(Long id);

    // Dimension CRUD
    List<OntDimensionDTO> listDimensionsByEntity(Long entityId);

    OntDimensionDTO createDimension(DimensionCreateRequest request);

    OntDimensionDTO updateDimension(Long id, DimensionCreateRequest request);

    void deleteDimension(Long id);
}
