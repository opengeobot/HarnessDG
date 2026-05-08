/**
 * 功能：周报 DTO
 * 时间：2026-05-08
 * 作者：AxeXie
 */
package com.harnessdg.model.report.dto;

import lombok.Data;

import java.time.OffsetDateTime;
import java.util.Map;

@Data
public class WeeklyReportDTO {

    private Long id;

    private String reportType;

    private String title;

    private OffsetDateTime timeRangeStart;

    private OffsetDateTime timeRangeEnd;

    private Map<String, Object> contentJson;

    private String markdownContent;

    private Map<String, Object> metricsSnapshot;

    private String generatedBy;

    private String agentSessionId;

    private String status;

    private String errorMessage;
}
