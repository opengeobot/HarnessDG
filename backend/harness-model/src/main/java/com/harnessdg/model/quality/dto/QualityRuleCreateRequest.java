/**
 * 功能：质量规则创建请求
 * 时间：2026-05-08
 * 作者：AxeXie
 */
package com.harnessdg.model.quality.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.util.Map;

@Data
public class QualityRuleCreateRequest {

    @NotNull(message = "entityId 不能为空")
    private Long entityId;

    private Long metricId;

    @NotBlank(message = "ruleType 不能为空")
    private String ruleType;

    @NotBlank(message = "ruleName 不能为空")
    private String ruleName;

    @NotBlank(message = "ruleExpression 不能为空")
    private String ruleExpression;

    private Map<String, Object> threshold;

    private String severity;

    private String scheduleCron;

    private String description;
}
