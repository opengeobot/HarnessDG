/**
 * 功能：审批实例 DTO
 * 时间：2026-05-08
 * 作者：AxeXie
 */
package com.harnessdg.model.approval.dto;

import lombok.Data;

import java.time.OffsetDateTime;
import java.util.List;

@Data
public class ApprovalInstanceDTO {

    private Long id;

    private Long templateId;

    private String businessType;

    private String businessId;

    private String title;

    private String status;

    private String initiator;

    private Integer currentStep;

    private String resultNote;

    private OffsetDateTime createdAt;

    private OffsetDateTime updatedAt;

    private List<ApprovalStepDTO> steps;
}
