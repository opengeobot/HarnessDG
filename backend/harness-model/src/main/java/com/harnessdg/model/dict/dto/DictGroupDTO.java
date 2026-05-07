package com.harnessdg.model.dict.dto;

import lombok.Data;

import java.util.Map;

@Data
public class DictGroupDTO {

    private Long id;

    private String code;

    private Map<String, String> name;

    private Map<String, String> description;

    private String category;

    private Boolean isTree;

    private Boolean isMultiple;

    private Boolean isEditable;

    private String status;

    private String resolvedName;

    private String resolvedDescription;
}
