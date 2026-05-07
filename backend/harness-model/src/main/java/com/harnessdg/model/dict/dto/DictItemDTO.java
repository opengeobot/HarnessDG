package com.harnessdg.model.dict.dto;

import lombok.Data;

import java.util.List;
import java.util.Map;

@Data
public class DictItemDTO {

    private Long id;

    private String groupCode;

    private Long parentId;

    private String code;

    private Map<String, String> label;

    private Map<String, String> description;

    private String value;

    private String icon;

    private String color;

    private Integer sortOrder;

    private Boolean isDefault;

    private Boolean isSystem;

    private String status;

    private Map<String, Object> extra;

    private String resolvedLabel;

    private String resolvedDescription;

    private List<DictItemDTO> children;
}
