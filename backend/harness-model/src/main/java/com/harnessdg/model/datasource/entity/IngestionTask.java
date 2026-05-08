package com.harnessdg.model.datasource.entity;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.extension.handlers.JacksonTypeHandler;
import com.harnessdg.common.base.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.OffsetDateTime;
import java.util.Map;

/**
 * 功能：数据接入任务 Entity
 * 时间：2026-05-08
 * 作者：AxeXie
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName(value = "dss_ingestion_task", autoResultMap = true)
public class IngestionTask extends BaseEntity {

    private Long sourceId;

    private Long targetEntityId;

    private String taskName;

    private String taskCode;

    private String syncMode;

    private String sourceTable;

    @TableField(typeHandler = JacksonTypeHandler.class)
    private Map<String, Object> fieldMapping;

    private String scheduleCron;

    private String seatunnelJobId;

    private String dagsterRunId;

    private String status;

    private OffsetDateTime lastSyncAt;

    private Long lastSyncRows;

    private String errorMessage;

    private Long approvalInstanceId;
}
