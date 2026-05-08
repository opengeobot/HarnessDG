package com.harnessdg.model.quality.entity;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.extension.handlers.JacksonTypeHandler;
import com.harnessdg.common.base.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.OffsetDateTime;
import java.util.Map;

/**
 * 功能：质量检查记录 Entity
 * 时间：2026-05-08
 * 作者：AxeXie
 */
@Data
@TableName(value = "gov_quality_check", autoResultMap = true)
public class QualityCheck {

    private Long id;

    private Long ruleId;

    private Long taskId;

    private String checkResult;

    @TableField(typeHandler = JacksonTypeHandler.class)
    private Map<String, Object> actualValue;

    @TableField(typeHandler = JacksonTypeHandler.class)
    private Map<String, Object> expectedValue;

    private Boolean violated;

    private String errorMessage;

    private OffsetDateTime checkedAt;

    private OffsetDateTime createdAt;
}
