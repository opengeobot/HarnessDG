/**
 * 功能：质量检查记录 DTO
 * 时间：2026-05-08
 * 作者：AxeXie
 */
package com.harnessdg.model.quality.dto;

import lombok.Data;

import java.time.OffsetDateTime;
import java.util.Map;

@Data
public class QualityCheckDTO {

    private Long id;

    private Long ruleId;

    private Long taskId;

    private String checkResult;

    private Map<String, Object> actualValue;

    private Map<String, Object> expectedValue;

    private Boolean violated;

    private String errorMessage;

    private OffsetDateTime checkedAt;

    private OffsetDateTime createdAt;
}
