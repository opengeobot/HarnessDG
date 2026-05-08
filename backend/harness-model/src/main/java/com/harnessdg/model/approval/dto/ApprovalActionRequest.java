/**
 * 功能：审批动作请求（通过/驳回）
 * 时间：2026-05-08
 * 作者：AxeXie
 */
package com.harnessdg.model.approval.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class ApprovalActionRequest {

    @NotBlank(message = "审批人不能为空")
    private String approver;

    private String comment;
}
