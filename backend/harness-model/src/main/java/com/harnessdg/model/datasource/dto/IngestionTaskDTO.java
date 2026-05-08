/**
 * 功能：数据接入任务 DTO
 * 时间：2026-05-08
 * 作者：AxeXie
 */
package com.harnessdg.model.datasource.dto;

import lombok.Data;

import java.time.OffsetDateTime;
import java.util.Map;

@Data
public class IngestionTaskDTO {

    private Long id;

    private Long sourceId;

    private Long targetEntityId;

    private String taskName;

    private String taskCode;

    private String syncMode;

    private String sourceTable;

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
