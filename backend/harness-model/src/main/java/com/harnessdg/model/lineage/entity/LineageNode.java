package com.harnessdg.model.lineage.entity;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.extension.handlers.JacksonTypeHandler;
import com.harnessdg.common.base.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.util.Map;

/**
 * 功能：血缘节点 Entity
 * 时间：2026-05-08
 * 作者：AxeXie
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName(value = "gov_lineage_node", autoResultMap = true)
public class LineageNode extends BaseEntity {

    private String nodeType;

    private String nodeKey;

    @TableField(typeHandler = JacksonTypeHandler.class)
    private Map<String, String> name;

    private Long entityId;

    private Long metricId;

    private String dataSource;

    @TableField(typeHandler = JacksonTypeHandler.class)
    private Map<String, Object> extra;
}
