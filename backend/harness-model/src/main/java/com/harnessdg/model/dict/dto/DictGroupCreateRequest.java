package com.harnessdg.model.dict.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.util.Map;

@Data
public class DictGroupCreateRequest {

    @NotBlank(message = "字典分组编码不能为空")
    private String code;

    @NotNull(message = "字典分组名称不能为空")
    private Map<String, String> name;

    private Map<String, String> description;

    private String category = "business";

    private Boolean isTree = false;

    private Boolean isMultiple = false;

    private Boolean isEditable = true;
}
