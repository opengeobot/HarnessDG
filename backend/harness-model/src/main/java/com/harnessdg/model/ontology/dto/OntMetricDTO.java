package com.harnessdg.model.ontology.dto;

import lombok.Data;

import java.util.Map;

@Data
public class OntMetricDTO {

    private Long id;
    private String code;
    private Map<String, String> name;
    private Map<String, String> description;
    private Long entityId;
    private String metricType;
    private String aggMethod;
    private String expression;
    private String unit;
    private String owner;
    private String status;
    private Map<String, Object> tags;
    private String resolvedName;
}
