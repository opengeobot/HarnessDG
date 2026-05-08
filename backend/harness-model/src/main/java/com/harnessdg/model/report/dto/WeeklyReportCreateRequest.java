/**
 * 功能：周报创建请求
 * 时间：2026-05-08
 * 作者：AxeXie
 */
package com.harnessdg.model.report.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

import java.time.OffsetDateTime;
import java.util.Map;

@Data
public class WeeklyReportCreateRequest {

    @NotBlank(message = "reportType 不能为空")
    private String reportType;

    @NotBlank(message = "title 不能为空")
    private String title;

    private OffsetDateTime timeRangeStart;

    private OffsetDateTime timeRangeEnd;

    private Map<String, Object> contentJson;

    private String markdownContent;

    private String generatedBy;

    private String agentSessionId;
}
