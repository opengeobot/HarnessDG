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
@TableName(value = "ont_entity", autoResultMap = true)
public class OntEntity extends BaseEntity {

    private String code;

    @TableField(typeHandler = JacksonTypeHandler.class)
    private Map<String, String> name;

    @TableField(typeHandler = JacksonTypeHandler.class)
    private Map<String, String> description;

    @TableField("entity_type")
    private String entityType;

    @TableField("data_domain")
    private String dataDomain;

    private String owner;

    private String status;

    @TableField(typeHandler = JacksonTypeHandler.class)
    private Map<String, Object> tags;
}
