/**
 * 功能：审批模板 DTO
 * 时间：2026-05-08
 * 作者：AxeXie
 */
package com.harnessdg.model.approval.dto;

import lombok.Data;

import java.util.List;
import java.util.Map;

@Data
public class ApprovalTemplateDTO {

    private Long id;

    private String businessType;

    private String code;

    private Map<String, String> name;

    private Map<String, String> description;

    private List<Map<String, Object>> stepsJson;

    private Boolean isActive;
}
