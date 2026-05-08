package com.harnessdg.model.lineage.entity;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.extension.handlers.JacksonTypeHandler;
import com.harnessdg.common.base.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.util.Map;

/**
 * 功能：血缘边 Entity
 * 时间：2026-05-08
 * 作者：AxeXie
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName(value = "gov_lineage_edge", autoResultMap = true)
public class LineageEdge extends BaseEntity {

    private Long sourceNodeId;

    private Long targetNodeId;

    private String edgeType;

    private String transformLogic;

    @TableField(typeHandler = JacksonTypeHandler.class)
    private Map<String, Object> extra;
}
