/**
 * 功能：数据接入任务创建请求
 * 时间：2026-05-08
 * 作者：AxeXie
 */
package com.harnessdg.model.datasource.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.util.Map;

@Data
public class IngestionTaskCreateRequest {

    @NotNull(message = "sourceId 不能为空")
    private Long sourceId;

    private Long targetEntityId;

    @NotBlank(message = "taskName 不能为空")
    private String taskName;

    @NotBlank(message = "taskCode 不能为空")
    private String taskCode;

    @NotBlank(message = "syncMode 不能为空")
    private String syncMode;

    @NotBlank(message = "sourceTable 不能为空")
    private String sourceTable;

    private Map<String, Object> fieldMapping;

    private String scheduleCron;
}
