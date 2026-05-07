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

    private String dimType;

    private String tableColumn;

    private String hierarchyLevel;

    private String status;

    @TableField(typeHandler = JacksonTypeHandler.class)
    private Map<String, Object> tags;
}
