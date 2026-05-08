package com.harnessdg.model.ontology.entity;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.extension.handlers.JacksonTypeHandler;
import com.harnessdg.common.base.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.util.Map;

/**
 * 功能：实体关系 Entity
 * 时间：2026-05-08
 * 作者：AxeXie
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName(value = "ont_relation", autoResultMap = true)
public class OntRelation extends BaseEntity {

    private Long sourceEntityId;

    private Long targetEntityId;

    private String relationType;

    @TableField(typeHandler = JacksonTypeHandler.class)
    private Map<String, String> name;

    @TableField(typeHandler = JacksonTypeHandler.class)
    private Map<String, String> description;

    private String cardinality;

    @TableField(typeHandler = JacksonTypeHandler.class)
    private Map<String, Object> extra;

    private String status;
}
