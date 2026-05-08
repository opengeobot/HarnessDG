/**
 * 功能：审批流服务实现（包含审批状态机逻辑）
 * 时间：2026-05-08
 * 作者：AxeXie
 */
package com.harnessdg.approval.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.harnessdg.approval.mapper.ApprovalInstanceMapper;
import com.harnessdg.approval.mapper.ApprovalStepMapper;
import com.harnessdg.approval.mapper.ApprovalTemplateMapper;
import com.harnessdg.approval.service.ApprovalService;
import com.harnessdg.common.exception.BizException;
import com.harnessdg.common.response.ErrorCode;
import com.harnessdg.model.approval.dto.*;
import com.harnessdg.model.approval.entity.ApprovalInstance;
import com.harnessdg.model.approval.entity.ApprovalStep;
import com.harnessdg.model.approval.entity.ApprovalTemplate;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class ApprovalServiceImpl implements ApprovalService {

    private final ApprovalTemplateMapper templateMapper;
    private final ApprovalInstanceMapper instanceMapper;
    private final ApprovalStepMapper stepMapper;

    // 审批状态常量
    private static final String STATUS_PENDING = "pending";
    private static final String STATUS_RUNNING = "running";
    private static final String STATUS_APPROVED = "approved";
    private static final String STATUS_REJECTED = "rejected";

    // 步骤状态常量
    private static final String STEP_STATUS_PENDING = "pending";
    private static final String STEP_STATUS_RUNNING = "running";
    private static final String STEP_STATUS_APPROVED = "approved";
    private static final String STEP_STATUS_REJECTED = "rejected";

    // 审批动作常量
    private static final String ACTION_APPROVE = "approve";
    private static final String ACTION_REJECT = "reject";

    @Override
    public List<ApprovalTemplateDTO> listTemplates(String businessType, Boolean isActive) {
        LambdaQueryWrapper<ApprovalTemplate> wrapper = new LambdaQueryWrapper<>();
        if (businessType != null && !businessType.isBlank()) {
            wrapper.eq(ApprovalTemplate::getBusinessType, businessType);
        }
        if (isActive != null) {
            wrapper.eq(ApprovalTemplate::getIsActive, isActive);
        }
        wrapper.orderByAsc(ApprovalTemplate::getCode);
        return templateMapper.selectList(wrapper).stream()
                .map(this::toTemplateDTO)
                .collect(Collectors.toList());
    }

    @Override
    public ApprovalTemplateDTO getTemplateById(Long id) {
        ApprovalTemplate template = findTemplateById(id);
        return toTemplateDTO(template);
    }

    @Override
    @Transactional
    public ApprovalTemplateDTO createTemplate(ApprovalTemplateDTO request) {
        // 检查 code 唯一性
        LambdaQueryWrapper<ApprovalTemplate> check = new LambdaQueryWrapper<>();
        check.eq(ApprovalTemplate::getCode, request.getCode());
        if (templateMapper.selectCount(check) > 0) {
            throw new BizException(ErrorCode.DUPLICATE);
        }

        ApprovalTemplate entity = new ApprovalTemplate();
        entity.setBusinessType(request.getBusinessType());
        entity.setCode(request.getCode());
        entity.setName(request.getName());
        entity.setDescription(request.getDescription());
        entity.setStepsJson(request.getStepsJson());
        entity.setIsActive(request.getIsActive() != null ? request.getIsActive() : false);
        templateMapper.insert(entity);
        return toTemplateDTO(entity);
    }

    @Override
    @Transactional
    public ApprovalInstanceDTO createInstance(ApprovalCreateRequest request) {
        // 验证模板是否存在
        ApprovalTemplate template = findTemplateById(request.getTemplateId());

        // 创建审批实例
        ApprovalInstance instance = new ApprovalInstance();
        instance.setTemplateId(request.getTemplateId());
        instance.setBusinessType(request.getBusinessType());
        instance.setBusinessId(request.getBusinessId());
        instance.setTitle(request.getTitle());
        instance.setInitiator(request.getInitiator());
        instance.setStatus(STATUS_RUNNING);
        instance.setCurrentStep(1);
        instanceMapper.insert(instance);

        // 根据模板的 stepsJson 生成审批步骤
        if (template.getStepsJson() != null && !template.getStepsJson().isEmpty()) {
            List<ApprovalStep> steps = new ArrayList<>();
            for (int i = 0; i < template.getStepsJson().size(); i++) {
                Map<String, Object> stepConfig = template.getStepsJson().get(i);
                ApprovalStep step = new ApprovalStep();
                step.setInstanceId(instance.getId());
                step.setStepOrder(i + 1);

                // 从配置中提取审批角色和用户
                if (stepConfig.containsKey("approverRole")) {
                    step.setApproverRole(String.valueOf(stepConfig.get("approverRole")));
                }
                if (stepConfig.containsKey("approverUser")) {
                    step.setApproverUser(String.valueOf(stepConfig.get("approverUser")));
                }

                // 第一个步骤设置为 running，其余为 pending
                step.setStatus(i == 0 ? STEP_STATUS_RUNNING : STEP_STATUS_PENDING);
                stepMapper.insert(step);
                steps.add(step);
            }
        }

        return getInstanceById(instance.getId());
    }

    @Override
    public List<ApprovalInstanceDTO> listInstances(String status, String initiator) {
        LambdaQueryWrapper<ApprovalInstance> wrapper = new LambdaQueryWrapper<>();
        if (status != null && !status.isBlank()) {
            wrapper.eq(ApprovalInstance::getStatus, status);
        }
        if (initiator != null && !initiator.isBlank()) {
            wrapper.eq(ApprovalInstance::getInitiator, initiator);
        }
        wrapper.orderByDesc(ApprovalInstance::getCreatedAt);
        return instanceMapper.selectList(wrapper).stream()
                .map(this::toInstanceDTO)
                .collect(Collectors.toList());
    }

    @Override
    public ApprovalInstanceDTO getInstanceById(Long id) {
        ApprovalInstance instance = findInstanceById(id);
        ApprovalInstanceDTO dto = toInstanceDTO(instance);

        // 加载关联的步骤列表
        LambdaQueryWrapper<ApprovalStep> stepWrapper = new LambdaQueryWrapper<>();
        stepWrapper.eq(ApprovalStep::getInstanceId, id)
                .orderByAsc(ApprovalStep::getStepOrder);
        List<ApprovalStepDTO> steps = stepMapper.selectList(stepWrapper).stream()
                .map(this::toStepDTO)
                .collect(Collectors.toList());
        dto.setSteps(steps);

        return dto;
    }

    @Override
    @Transactional
    public ApprovalInstanceDTO approve(Long instanceId, Long stepId, ApprovalActionRequest request) {
        return processApproval(instanceId, stepId, request, ACTION_APPROVE);
    }

    @Override
    @Transactional
    public ApprovalInstanceDTO reject(Long instanceId, Long stepId, ApprovalActionRequest request) {
        return processApproval(instanceId, stepId, request, ACTION_REJECT);
    }

    @Override
    public List<ApprovalInstanceDTO> getMyTodoList(String approver) {
        // 查找当前步骤待审批且审批人是当前用户的实例
        LambdaQueryWrapper<ApprovalStep> stepWrapper = new LambdaQueryWrapper<>();
        stepWrapper.eq(ApprovalStep::getStatus, STEP_STATUS_RUNNING)
                .and(w -> w.eq(ApprovalStep::getApproverUser, approver)
                        .or().eq(ApprovalStep::getApproverRole, approver));

        List<Long> instanceIds = stepMapper.selectList(stepWrapper).stream()
                .map(ApprovalStep::getInstanceId)
                .distinct()
                .collect(Collectors.toList());

        if (instanceIds.isEmpty()) {
            return new ArrayList<>();
        }

        LambdaQueryWrapper<ApprovalInstance> instanceWrapper = new LambdaQueryWrapper<>();
        instanceWrapper.in(ApprovalInstance::getId, instanceIds)
                .eq(ApprovalInstance::getStatus, STATUS_RUNNING)
                .orderByDesc(ApprovalInstance::getCreatedAt);

        return instanceMapper.selectList(instanceWrapper).stream()
                .map(this::toInstanceDTO)
                .collect(Collectors.toList());
    }

    // === 核心审批状态机处理逻辑 ===

    private ApprovalInstanceDTO processApproval(Long instanceId, Long stepId,
                                                  ApprovalActionRequest request, String action) {
        // 1. 验证审批实例
        ApprovalInstance instance = findInstanceById(instanceId);

        // 检查是否已经完成
        if (STATUS_APPROVED.equals(instance.getStatus()) || STATUS_REJECTED.equals(instance.getStatus())) {
            throw new BizException(ErrorCode.APPROVAL_ALREADY_COMPLETED);
        }

        // 2. 验证审批步骤
        ApprovalStep step = findStepById(stepId);

        // 验证步骤是否属于该实例
        if (!step.getInstanceId().equals(instanceId)) {
            throw new BizException(ErrorCode.APPROVAL_STEP_NOT_FOUND);
        }

        // 验证步骤是否正在等待审批
        if (!STEP_STATUS_RUNNING.equals(step.getStatus())) {
            throw new BizException(ErrorCode.APPROVAL_NOT_YOUR_TURN);
        }

        // 3. 更新当前步骤状态
        step.setStatus(ACTION_APPROVE.equals(action) ? STEP_STATUS_APPROVED : STEP_STATUS_REJECTED);
        step.setAction(action);
        step.setComment(request.getComment());
        step.setDecidedAt(OffsetDateTime.now());
        stepMapper.updateById(step);

        // 4. 根据审批结果推进状态机
        if (ACTION_REJECT.equals(action)) {
            // 驳回：整个审批流程结束
            instance.setStatus(STATUS_REJECTED);
            instance.setResultNote("已驳回: " + (request.getComment() != null ? request.getComment() : ""));
            instanceMapper.updateById(instance);
        } else {
            // 通过：检查是否还有下一步
            LambdaQueryWrapper<ApprovalStep> nextStepWrapper = new LambdaQueryWrapper<>();
            nextStepWrapper.eq(ApprovalStep::getInstanceId, instanceId)
                    .eq(ApprovalStep::getStatus, STEP_STATUS_PENDING)
                    .orderByAsc(ApprovalStep::getStepOrder)
                    .last("LIMIT 1");
            ApprovalStep nextStep = stepMapper.selectOne(nextStepWrapper);

            if (nextStep != null) {
                // 还有下一步，激活下一个步骤
                nextStep.setStatus(STEP_STATUS_RUNNING);
                stepMapper.updateById(nextStep);
                instance.setCurrentStep(nextStep.getStepOrder());
            } else {
                // 所有步骤已完成
                instance.setStatus(STATUS_APPROVED);
                instance.setResultNote("审批通过");
            }
            instanceMapper.updateById(instance);
        }

        return getInstanceById(instanceId);
    }

    // === Private helpers ===

    private ApprovalTemplate findTemplateById(Long id) {
        ApprovalTemplate template = templateMapper.selectById(id);
        if (template == null) {
            throw new BizException(ErrorCode.APPROVAL_TEMPLATE_NOT_FOUND);
        }
        return template;
    }

    private ApprovalInstance findInstanceById(Long id) {
        ApprovalInstance instance = instanceMapper.selectById(id);
        if (instance == null) {
            throw new BizException(ErrorCode.APPROVAL_INSTANCE_NOT_FOUND);
        }
        return instance;
    }

    private ApprovalStep findStepById(Long id) {
        ApprovalStep step = stepMapper.selectById(id);
        if (step == null) {
            throw new BizException(ErrorCode.APPROVAL_STEP_NOT_FOUND);
        }
        return step;
    }

    private ApprovalTemplateDTO toTemplateDTO(ApprovalTemplate entity) {
        ApprovalTemplateDTO dto = new ApprovalTemplateDTO();
        dto.setId(entity.getId());
        dto.setBusinessType(entity.getBusinessType());
        dto.setCode(entity.getCode());
        dto.setName(entity.getName());
        dto.setDescription(entity.getDescription());
        dto.setStepsJson(entity.getStepsJson());
        dto.setIsActive(entity.getIsActive());
        return dto;
    }

    private ApprovalInstanceDTO toInstanceDTO(ApprovalInstance entity) {
        ApprovalInstanceDTO dto = new ApprovalInstanceDTO();
        dto.setId(entity.getId());
        dto.setTemplateId(entity.getTemplateId());
        dto.setBusinessType(entity.getBusinessType());
        dto.setBusinessId(entity.getBusinessId());
        dto.setTitle(entity.getTitle());
        dto.setStatus(entity.getStatus());
        dto.setInitiator(entity.getInitiator());
        dto.setCurrentStep(entity.getCurrentStep());
        dto.setResultNote(entity.getResultNote());
        dto.setCreatedAt(entity.getCreatedAt());
        dto.setUpdatedAt(entity.getUpdatedAt());
        return dto;
    }

    private ApprovalStepDTO toStepDTO(ApprovalStep entity) {
        ApprovalStepDTO dto = new ApprovalStepDTO();
        dto.setId(entity.getId());
        dto.setInstanceId(entity.getInstanceId());
        dto.setStepOrder(entity.getStepOrder());
        dto.setApproverRole(entity.getApproverRole());
        dto.setApproverUser(entity.getApproverUser());
        dto.setStatus(entity.getStatus());
        dto.setAction(entity.getAction());
        dto.setComment(entity.getComment());
        dto.setDecidedAt(entity.getDecidedAt());
        return dto;
    }
}
