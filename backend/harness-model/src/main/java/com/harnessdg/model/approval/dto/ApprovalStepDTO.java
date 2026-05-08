/**
 * 功能：审批步骤 DTO
 * 时间：2026-05-08
 * 作者：AxeXie
 */
package com.harnessdg.model.approval.dto;

import lombok.Data;

import java.time.OffsetDateTime;

@Data
public class ApprovalStepDTO {

    private Long id;

    private Long instanceId;

    private Integer stepOrder;

    private String approverRole;

    private String approverUser;

    private String status;

    private String action;

    private String comment;

    private OffsetDateTime decidedAt;
}
