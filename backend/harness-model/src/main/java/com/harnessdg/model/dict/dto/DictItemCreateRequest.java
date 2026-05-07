package com.harnessdg.model.dict.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.util.Map;

@Data
public class DictItemCreateRequest {

    @NotBlank(message = "字典分组编码不能为空")
    private String groupCode;

    private Long parentId;

    @NotBlank(message = "字典项编码不能为空")
    private String code;

    @NotNull(message = "字典项标签不能为空")
    private Map<String, String> label;

    private Map<String, String> description;

    @NotBlank(message = "字典项值不能为空")
    private String value;

    private String icon;

    private String color;

    private Integer sortOrder = 0;

    private Boolean isDefault = false;

    private Map<String, Object> extra;
}
