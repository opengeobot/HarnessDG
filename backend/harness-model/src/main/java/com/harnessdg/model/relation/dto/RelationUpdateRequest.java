package com.harnessdg.model.relation.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

import java.util.Map;

/**
 * 功能：实体关系更新请求
 * 时间：2026-05-08
 * 作者：AxeXie
 */
@Data
public class RelationUpdateRequest {

    @NotBlank(message = "关系类型不能为空")
    private String relationType;

    private Map<String, String> name;

    private Map<String, String> description;

    private String cardinality;

    private Map<String, Object> extra;

    private String status;
}
