package com.harnessdg.model.relation.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.util.Map;

/**
 * 功能：实体关系创建请求
 * 时间：2026-05-08
 * 作者：AxeXie
 */
@Data
public class RelationCreateRequest {

    @NotNull(message = "源实体ID不能为空")
    private Long sourceEntityId;

    @NotNull(message = "目标实体ID不能为空")
    private Long targetEntityId;

    @NotBlank(message = "关系类型不能为空")
    private String relationType;

    private Map<String, String> name;

    private Map<String, String> description;

    private String cardinality;

    private Map<String, Object> extra;
}
