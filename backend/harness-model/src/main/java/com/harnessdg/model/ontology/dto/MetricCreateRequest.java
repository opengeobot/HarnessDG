package com.harnessdg.model.ontology.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.util.Map;

@Data
public class MetricCreateRequest {

    @NotBlank
    private String code;

    @NotNull
    private Map<String, String> name;

    private Map<String, String> description;

    @NotNull
    private Long entityId;

    @NotBlank
    private String metricType;

    @NotBlank
    private String aggMethod;

    private String expression;

    private String unit;

    private String owner;

    private Map<String, Object> tags;
}
