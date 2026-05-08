package com.harnessdg.model.approval.entity;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.extension.handlers.JacksonTypeHandler;
import com.harnessdg.common.base.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.util.List;
import java.util.Map;

/**
 * 功能：审批模板 Entity
 * 时间：2026-05-08
 * 作者：AxeXie
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName(value = "gov_approval_template", autoResultMap = true)
public class ApprovalTemplate extends BaseEntity {

    private String businessType;

    private String code;

    @TableField(typeHandler = JacksonTypeHandler.class)
    private Map<String, String> name;

    @TableField(typeHandler = JacksonTypeHandler.class)
    private Map<String, String> description;

    @TableField(typeHandler = JacksonTypeHandler.class)
    private List<Map<String, Object>> stepsJson;

    private Boolean isActive;
}
