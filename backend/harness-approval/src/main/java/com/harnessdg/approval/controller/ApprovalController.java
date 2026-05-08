/**
 * 功能：审批流控制器
 * 时间：2026-05-08
 * 作者：AxeXie
 */
package com.harnessdg.approval.controller;

import com.harnessdg.approval.service.ApprovalService;
import com.harnessdg.common.response.R;
import com.harnessdg.model.approval.dto.*;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/approval")
@RequiredArgsConstructor
public class ApprovalController {

    private final ApprovalService approvalService;

    /**
     * 审批模板列表
     */
    @GetMapping("/templates")
    public R<List<ApprovalTemplateDTO>> listTemplates(
            @RequestParam(required = false) String businessType,
            @RequestParam(required = false) Boolean isActive) {
        return R.ok(approvalService.listTemplates(businessType, isActive));
    }

    /**
     * 创建审批模板
     */
    @PostMapping("/templates")
    public R<ApprovalTemplateDTO> createTemplate(@Valid @RequestBody ApprovalTemplateDTO request) {
        return R.ok(approvalService.createTemplate(request));
    }

    /**
     * 发起审批
     */
    @PostMapping("/instances")
    public R<ApprovalInstanceDTO> createInstance(@Valid @RequestBody ApprovalCreateRequest request) {
        return R.ok(approvalService.createInstance(request));
    }

    /**
     * 审批列表（支持 status/initiator 过滤）
     */
    @GetMapping("/instances")
    public R<List<ApprovalInstanceDTO>> listInstances(
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String initiator) {
        return R.ok(approvalService.listInstances(status, initiator));
    }

    /**
     * 审批详情（含步骤列表）
     */
    @GetMapping("/instances/{id}")
    public R<ApprovalInstanceDTO> getInstance(@PathVariable Long id) {
        return R.ok(approvalService.getInstanceById(id));
    }

    /**
     * 审批通过
     */
    @PostMapping("/instances/{id}/steps/{stepId}/approve")
    public R<ApprovalInstanceDTO> approve(
            @PathVariable Long id,
            @PathVariable Long stepId,
            @Valid @RequestBody ApprovalActionRequest request) {
        return R.ok(approvalService.approve(id, stepId, request));
    }

    /**
     * 审批驳回
     */
    @PostMapping("/instances/{id}/steps/{stepId}/reject")
    public R<ApprovalInstanceDTO> reject(
            @PathVariable Long id,
            @PathVariable Long stepId,
            @Valid @RequestBody ApprovalActionRequest request) {
        return R.ok(approvalService.reject(id, stepId, request));
    }

    /**
     * 我的待审批
     */
    @GetMapping("/my-todo")
    public R<List<ApprovalInstanceDTO>> getMyTodo(
            @RequestParam String approver) {
        return R.ok(approvalService.getMyTodoList(approver));
    }
}
