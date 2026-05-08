package com.harnessdg.model.report.entity;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.extension.handlers.JacksonTypeHandler;
import com.harnessdg.common.base.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.OffsetDateTime;
import java.util.Map;

/**
 * 功能：周报 Entity
 * 时间：2026-05-08
 * 作者：AxeXie
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName(value = "rpt_weekly_report", autoResultMap = true)
public class WeeklyReport extends BaseEntity {

    private String reportType;

    private String title;

    private OffsetDateTime timeRangeStart;

    private OffsetDateTime timeRangeEnd;

    @TableField(typeHandler = JacksonTypeHandler.class)
    private Map<String, Object> contentJson;

    private String markdownContent;

    @TableField(typeHandler = JacksonTypeHandler.class)
    private Map<String, Object> metricsSnapshot;

    private String generatedBy;

    private String agentSessionId;

    private String status;

    private String errorMessage;
}
