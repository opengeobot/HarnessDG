package com.harnessdg.model.approval.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.harnessdg.common.base.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.OffsetDateTime;

/**
 * 功能：审批步骤 Entity
 * 时间：2026-05-08
 * 作者：AxeXie
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("gov_approval_step")
public class ApprovalStep extends BaseEntity {

    private Long instanceId;

    private Integer stepOrder;

    private String approverRole;

    private String approverUser;

    private String status;

    private String action;

    private String comment;

    private OffsetDateTime decidedAt;
}
