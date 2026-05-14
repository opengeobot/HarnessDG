package com.harnessdg.model.ontology.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.util.Map;

@Data
public class DimensionCreateRequest {

    @NotBlank
    private String code;

    @NotNull
    private Map<String, String> name;

    private Map<String, String> description;

    private Long entityId;

    private String dimType;

    private String dimensionType;

    private String dataType;

    private String tableColumn;

    private String hierarchyLevel;

    private Map<String, Object> hierarchyLevels;

    private Map<String, Object> tags;
}
