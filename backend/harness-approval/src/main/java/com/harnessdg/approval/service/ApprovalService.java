/**
 * 功能：审批流服务接口
 * 时间：2026-05-08
 * 作者：AxeXie
 */
package com.harnessdg.approval.service;

import com.harnessdg.model.approval.dto.*;

import java.util.List;

public interface ApprovalService {

    /**
     * 获取审批模板列表
     */
    List<ApprovalTemplateDTO> listTemplates(String businessType, Boolean isActive);

    /**
     * 根据ID获取审批模板详情
     */
    ApprovalTemplateDTO getTemplateById(Long id);

    /**
     * 创建审批模板
     */
    ApprovalTemplateDTO createTemplate(ApprovalTemplateDTO request);

    /**
     * 发起审批流程
     */
    ApprovalInstanceDTO createInstance(ApprovalCreateRequest request);

    /**
     * 获取审批实例列表（支持按状态和发起人过滤）
     */
    List<ApprovalInstanceDTO> listInstances(String status, String initiator);

    /**
     * 获取审批实例详情（包含步骤列表）
     */
    ApprovalInstanceDTO getInstanceById(Long id);

    /**
     * 审批通过
     */
    ApprovalInstanceDTO approve(Long instanceId, Long stepId, ApprovalActionRequest request);

    /**
     * 审批驳回
     */
    ApprovalInstanceDTO reject(Long instanceId, Long stepId, ApprovalActionRequest request);

    /**
     * 获取我的待审批列表
     */
    List<ApprovalInstanceDTO> getMyTodoList(String approver);
}
