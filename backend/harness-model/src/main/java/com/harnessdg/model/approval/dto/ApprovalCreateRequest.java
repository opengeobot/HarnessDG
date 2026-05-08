/**
 * 功能：创建审批实例请求
 * 时间：2026-05-08
 * 作者：AxeXie
 */
package com.harnessdg.model.approval.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class ApprovalCreateRequest {

    @NotNull(message = "审批模板ID不能为空")
    private Long templateId;

    @NotBlank(message = "业务类型不能为空")
    private String businessType;

    @NotBlank(message = "业务ID不能为空")
    private String businessId;

    @NotBlank(message = "审批标题不能为空")
    private String title;

    @NotBlank(message = "发起人不能为空")
    private String initiator;
}
