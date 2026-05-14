package com.harnessdg.model.ontology.dto;

import lombok.Data;

import java.util.Map;

@Data
public class OntDimensionDTO {

    private Long id;
    private String code;
    private Map<String, String> name;
    private Map<String, String> description;
    private Long entityId;
    private String dimType;
    private String dimensionType;
    private String dataType;
    private String tableColumn;
    private String hierarchyLevel;
    private Map<String, Object> hierarchyLevels;
    private String status;
    private Map<String, Object> tags;
    private String resolvedName;
}
