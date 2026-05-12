/**
 * 功能：质量检查结果 DTO
 * 时间：2026-05-12
 * 作者：AxeXie
 */
package com.harnessdg.model.quality.dto;

import lombok.Data;

import java.time.OffsetDateTime;
import java.util.Map;

@Data
public class QualityCheckResultDTO {

    private Long id;

    private Long ruleId;

    private String ruleName;

    private String ruleType;

    private Long entityId;

    private Long metricId;

    private String checkResult;

    private Map<String, Object> actualValue;

    private Map<String, Object> expectedValue;

    private Boolean violated;

    private String errorMessage;

    private OffsetDateTime checkedAt;
}
