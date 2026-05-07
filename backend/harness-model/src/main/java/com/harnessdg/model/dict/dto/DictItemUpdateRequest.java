package com.harnessdg.model.dict.dto;

import lombok.Data;

import java.util.Map;

@Data
public class DictItemUpdateRequest {

    private Long parentId;

    private Map<String, String> label;

    private Map<String, String> description;

    private String value;

    private String icon;

    private String color;

    private Integer sortOrder;

    private Boolean isDefault;

    private String status;

    private Map<String, Object> extra;
}
