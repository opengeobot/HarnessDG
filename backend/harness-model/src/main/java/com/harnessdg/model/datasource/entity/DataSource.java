package com.harnessdg.model.datasource.entity;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.extension.handlers.JacksonTypeHandler;
import com.harnessdg.common.base.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.util.Map;

/**
 * 功能：数据源 Entity
 * 时间：2026-05-08
 * 作者：AxeXie
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName(value = "dss_data_source", autoResultMap = true)
public class DataSource extends BaseEntity {

    private String name;

    private String code;

    private String sourceType;

    @TableField(typeHandler = JacksonTypeHandler.class)
    private Map<String, Object> connectionConfig;

    @TableField(typeHandler = JacksonTypeHandler.class)
    private Map<String, String> description;

    private String status;

    private String owner;

    @TableField(typeHandler = JacksonTypeHandler.class)
    private Map<String, Object> tags;
}
