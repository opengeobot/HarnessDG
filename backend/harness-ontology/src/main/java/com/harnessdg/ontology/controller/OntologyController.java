package com.harnessdg.ontology.controller;

import com.harnessdg.common.page.PageRequest;
import com.harnessdg.common.page.PageResult;
import com.harnessdg.common.response.R;
import com.harnessdg.model.ontology.dto.*;
import com.harnessdg.ontology.service.OntologyService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/ontology")
@RequiredArgsConstructor
public class OntologyController {

    private final OntologyService ontologyService;

    // === Entity endpoints ===

    @GetMapping("/entities")
    public R<PageResult<OntEntityDTO>> listEntities(
            @RequestParam(required = false) String domain,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String keyword,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size) {
        PageRequest pageRequest = new PageRequest();
        pageRequest.setPage(page);
        pageRequest.setPageSize(size);
        return R.ok(ontologyService.listEntities(domain, status, keyword, pageRequest));
    }

    @GetMapping("/entities/{id}")
    public R<OntEntityDTO> getEntity(
            @PathVariable Long id,
            @RequestHeader(value = "Accept-Language", required = false) String locale) {
        return R.ok(ontologyService.getEntityById(id, locale));
    }

    @PostMapping("/entities")
    public R<OntEntityDTO> createEntity(@Valid @RequestBody EntityCreateRequest request) {
        return R.ok(ontologyService.createEntity(request));
    }

    @PutMapping("/entities/{id}")
    public R<OntEntityDTO> updateEntity(@PathVariable Long id,
                                         @Valid @RequestBody EntityCreateRequest request) {
        return R.ok(ontologyService.updateEntity(id, request));
    }

    @DeleteMapping("/entities/{id}")
    public R<Void> deleteEntity(@PathVariable Long id) {
        ontologyService.deleteEntity(id);
        return R.ok(null);
    }

    // === Metric endpoints ===

    @GetMapping("/entities/{entityId}/metrics")
    public R<List<OntMetricDTO>> listMetrics(@PathVariable Long entityId) {
        return R.ok(ontologyService.listMetricsByEntity(entityId));
    }

    @PostMapping("/metrics")
    public R<OntMetricDTO> createMetric(@Valid @RequestBody MetricCreateRequest request) {
        return R.ok(ontologyService.createMetric(request));
    }

    @PutMapping("/metrics/{id}")
    public R<OntMetricDTO> updateMetric(@PathVariable Long id,
                                         @Valid @RequestBody MetricCreateRequest request) {
        return R.ok(ontologyService.updateMetric(id, request));
    }

    @DeleteMapping("/metrics/{id}")
    public R<Void> deleteMetric(@PathVariable Long id) {
        ontologyService.deleteMetric(id);
        return R.ok(null);
    }

    // === Dimension endpoints ===

    @GetMapping("/entities/{entityId}/dimensions")
    public R<List<OntDimensionDTO>> listDimensions(@PathVariable Long entityId) {
        return R.ok(ontologyService.listDimensionsByEntity(entityId));
    }

    @PostMapping("/dimensions")
    public R<OntDimensionDTO> createDimension(@Valid @RequestBody DimensionCreateRequest request) {
        return R.ok(ontologyService.createDimension(request));
    }

    @PutMapping("/dimensions/{id}")
    public R<OntDimensionDTO> updateDimension(@PathVariable Long id,
                                               @Valid @RequestBody DimensionCreateRequest request) {
        return R.ok(ontologyService.updateDimension(id, request));
    }

    @DeleteMapping("/dimensions/{id}")
    public R<Void> deleteDimension(@PathVariable Long id) {
        ontologyService.deleteDimension(id);
        return R.ok(null);
    }
}
