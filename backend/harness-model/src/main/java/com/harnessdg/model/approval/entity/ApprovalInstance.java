package com.harnessdg.model.approval.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.harnessdg.common.base.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 功能：审批实例 Entity
 * 时间：2026-05-08
 * 作者：AxeXie
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("gov_approval_instance")
public class ApprovalInstance extends BaseEntity {

    private Long templateId;

    private String businessType;

    private String businessId;

    private String title;

    private String status;

    private String initiator;

    private Integer currentStep;

    private String resultNote;
}
