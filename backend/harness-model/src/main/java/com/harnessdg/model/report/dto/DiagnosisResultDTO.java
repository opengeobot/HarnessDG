/**
 * 功能：诊断结果 DTO
 * 时间：2026-05-12
 * 作者：AxeXie
 */
package com.harnessdg.model.report.dto;

import lombok.Data;

import java.time.OffsetDateTime;
import java.util.List;

@Data
public class DiagnosisResultDTO {

    private Long taskId;

    private String taskType;

    private String errorMessage;

    private String rootCauseCategory;

    private String rootCauseDescription;

    private List<String> fixSuggestions;

    private OffsetDateTime diagnosedAt;
}
