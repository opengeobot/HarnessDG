/**
 * 功能：质量规则自动生成请求
 * 时间：2026-05-08
 * 作者：AxeXie
 */
package com.harnessdg.model.quality.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class QualityRuleAutoGenerateRequest {

    @NotNull(message = "entityId 不能为空")
    private Long entityId;

    private Long metricId;

    private String severity;

    private String description;
}
