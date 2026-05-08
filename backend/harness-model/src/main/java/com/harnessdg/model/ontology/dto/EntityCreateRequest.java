package com.harnessdg.model.ontology.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.util.Map;

@Data
public class EntityCreateRequest {

    @NotBlank
    private String code;

    @NotNull
    private Map<String, String> name;

    private Map<String, String> description;

    private String entityType;

    @NotBlank
    private String dataDomain;

    private String owner;

    private Map<String, Object> tags;
}
