package com.harnessdg.model.ontology.dto;

import lombok.Data;

import java.util.List;
import java.util.Map;

@Data
public class OntEntityDTO {

    private Long id;
    private String code;
    private Map<String, String> name;
    private Map<String, String> description;
    private String entityType;
    private String dataDomain;
    private String owner;
    private String status;
    private Map<String, Object> tags;
    private String resolvedName;

    private List<OntMetricDTO> metrics;
    private List<OntDimensionDTO> dimensions;
}
