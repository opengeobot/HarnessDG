package com.harnessdg.model.ontology.entity;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.extension.handlers.JacksonTypeHandler;
import com.harnessdg.common.base.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.util.Map;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName(value = "ont_dimension", autoResultMap = true)
public class OntDimension extends BaseEntity {

    private String code;

    @TableField(typeHandler = JacksonTypeHandler.class)
    private Map<String, String> name;

    @TableField(typeHandler = JacksonTypeHandler.class)
    private Map<String, String> description;

    private Long entityId;

    @TableField("dimension_type")
    private String dimType;

    private String dataType;

    @TableField(exist = false)
    private String tableColumn;

    @TableField(exist = false)
    private String hierarchyLevel;

    @TableField(value = "hierarchy_levels", typeHandler = JacksonTypeHandler.class)
    private Map<String, Object> hierarchyLevels;

    private String status;

    @TableField(exist = false)
    private Map<String, Object> tags;
}
