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
@TableName(value = "ont_metric", autoResultMap = true)
public class OntMetric extends BaseEntity {

    private String code;

    @TableField(typeHandler = JacksonTypeHandler.class)
    private Map<String, String> name;

    @TableField(typeHandler = JacksonTypeHandler.class)
    private Map<String, String> description;

    private Long entityId;

    private String metricType;

    private String aggMethod;

    private String expression;

    private String unit;

    private String owner;

    private String status;

    @TableField(typeHandler = JacksonTypeHandler.class)
    private Map<String, Object> tags;
}
