/**
 * 功能：审批流服务单元测试（重点覆盖状态机逻辑）
 * 时间：2026-05-12
 * 作者：AxeXie
 */
package com.harnessdg.approval.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.harnessdg.approval.mapper.ApprovalInstanceMapper;
import com.harnessdg.approval.mapper.ApprovalStepMapper;
import com.harnessdg.approval.mapper.ApprovalTemplateMapper;
import com.harnessdg.approval.service.impl.ApprovalServiceImpl;
import com.harnessdg.common.exception.BizException;
import com.harnessdg.model.approval.dto.ApprovalActionRequest;
import com.harnessdg.model.approval.dto.ApprovalCreateRequest;
import com.harnessdg.model.approval.dto.ApprovalInstanceDTO;
import com.harnessdg.model.approval.dto.ApprovalTemplateDTO;
import com.harnessdg.model.approval.entity.ApprovalInstance;
import com.harnessdg.model.approval.entity.ApprovalStep;
import com.harnessdg.model.approval.entity.ApprovalTemplate;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ApprovalServiceTest {

    @Mock
    private ApprovalTemplateMapper templateMapper;

    @Mock
    private ApprovalInstanceMapper instanceMapper;

    @Mock
    private ApprovalStepMapper stepMapper;

    @InjectMocks
    private ApprovalServiceImpl approvalService;

    private ApprovalTemplate sampleTemplate;
    private ApprovalInstance sampleInstance;
    private ApprovalStep sampleStep;

    @BeforeEach
    void setUp() {
        // 初始化审批模板
        sampleTemplate = new ApprovalTemplate();
        sampleTemplate.setId(1L);
        sampleTemplate.setBusinessType("METRIC_PUBLISH");
        sampleTemplate.setCode("metric-publish-v1");
        sampleTemplate.setName(Map.of("zh", "指标发布审批", "en", "Metric Publish Approval"));
        sampleTemplate.setDescription(Map.of("zh", "指标发布需要审批", "en", "Metric publish requires approval"));
        sampleTemplate.setIsActive(true);
        sampleTemplate.setStepsJson(List.of(
                Map.of("approverRole", "data_steward", "approverUser", "admin"),
                Map.of("approverRole", "data_owner", "approverUser", "manager")
        ));

        // 初始化审批实例
        sampleInstance = new ApprovalInstance();
        sampleInstance.setId(1L);
        sampleInstance.setTemplateId(1L);
        sampleInstance.setBusinessType("METRIC_PUBLISH");
        sampleInstance.setBusinessId("metric-001");
        sampleInstance.setTitle("Test Metric Publish");
        sampleInstance.setInitiator("creator");
        sampleInstance.setStatus("running");
        sampleInstance.setCurrentStep(1);

        // 初始化审批步骤
        sampleStep = new ApprovalStep();
        sampleStep.setId(1L);
        sampleStep.setInstanceId(1L);
        sampleStep.setStepOrder(1);
        sampleStep.setApproverRole("data_steward");
        sampleStep.setApproverUser("admin");
        sampleStep.setStatus("running");
    }

    /**
     * 测试：创建审批模板成功
     * 验证：模板插入成功，返回 DTO 数据正确
     */
    @Test
    void testCreateTemplate_success() {
        // 准备请求数据
        ApprovalTemplateDTO request = new ApprovalTemplateDTO();
        request.setCode("new-template");
        request.setBusinessType("DATA_INGEST");
        request.setName(Map.of("zh", "新模板", "en", "New Template"));
        request.setDescription(Map.of("zh", "描述", "en", "Description"));
        request.setIsActive(true);
        request.setStepsJson(List.of(Map.of("approverRole", "admin")));

        // 模拟 code 唯一性检查
        when(templateMapper.selectCount(any(LambdaQueryWrapper.class))).thenReturn(0L);

        // 执行
        ApprovalTemplateDTO result = approvalService.createTemplate(request);

        // 验证
        assertNotNull(result);
        assertEquals("new-template", result.getCode());
        assertEquals("DATA_INGEST", result.getBusinessType());
        assertTrue(result.getIsActive());
        verify(templateMapper).insert(any(ApprovalTemplate.class));
    }

    /**
     * 测试：创建重复 code 的审批模板
     * 验证：抛出 BizException
     */
    @Test
    void testCreateTemplate_duplicateCode() {
        ApprovalTemplateDTO request = new ApprovalTemplateDTO();
        request.setCode("existing-code");
        request.setBusinessType("METRIC_PUBLISH");
        request.setName(Map.of("zh", "重复模板", "en", "Duplicate Template"));

        // 模拟 code 已存在
        when(templateMapper.selectCount(any(LambdaQueryWrapper.class))).thenReturn(1L);

        // 执行并验证异常
        assertThrows(BizException.class, () -> approvalService.createTemplate(request));
        verify(templateMapper, never()).insert(any(ApprovalTemplate.class));
    }

    /**
     * 测试：创建审批实例成功
     * 验证：实例和步骤创建成功，第一个步骤为 running
     */
    @Test
    void testCreateInstance_success() {
        // 准备请求
        ApprovalCreateRequest request = new ApprovalCreateRequest();
        request.setTemplateId(1L);
        request.setBusinessType("METRIC_PUBLISH");
        request.setBusinessId("metric-100");
        request.setTitle("Test Instance");
        request.setInitiator("user1");

        // 模拟模板查询
        when(templateMapper.selectById(1L)).thenReturn(sampleTemplate);

        // 模拟实例插入（设置自增 ID）
        doAnswer(invocation -> {
            ApprovalInstance instance = invocation.getArgument(0);
            instance.setId(10L); // 模拟数据库分配 ID
            return null;
        }).when(instanceMapper).insert(any(ApprovalInstance.class));

        // 模拟步骤插入
        doAnswer(invocation -> {
            ApprovalStep step = invocation.getArgument(0);
            step.setId(100L + step.getStepOrder()); // 模拟数据库分配 ID
            return null;
        }).when(stepMapper).insert(any(ApprovalStep.class));

        // 模拟 getInstanceById 返回结果
        ApprovalInstance newInstance = new ApprovalInstance();
        newInstance.setId(10L);
        newInstance.setTemplateId(1L);
        newInstance.setBusinessType("METRIC_PUBLISH");
        newInstance.setBusinessId("metric-100");
        newInstance.setTitle("Test Instance");
        newInstance.setInitiator("user1");
        newInstance.setStatus("running");
        newInstance.setCurrentStep(1);
        when(instanceMapper.selectById(10L)).thenReturn(newInstance);

        ApprovalStep step1 = new ApprovalStep();
        step1.setId(101L);
        step1.setInstanceId(10L);
        step1.setStepOrder(1);
        step1.setApproverRole("data_steward");
        step1.setApproverUser("admin");
        step1.setStatus("running");

        ApprovalStep step2 = new ApprovalStep();
        step2.setId(102L);
        step2.setInstanceId(10L);
        step2.setStepOrder(2);
        step2.setApproverRole("data_owner");
        step2.setApproverUser("manager");
        step2.setStatus("pending");

        when(stepMapper.selectList(any(LambdaQueryWrapper.class))).thenReturn(List.of(step1, step2));

        // 执行
        ApprovalInstanceDTO result = approvalService.createInstance(request);

        // 验证
        assertNotNull(result);
        assertEquals("running", result.getStatus());
        assertEquals(1, result.getCurrentStep());
        verify(instanceMapper).insert(any(ApprovalInstance.class));
        verify(stepMapper, times(2)).insert(any(ApprovalStep.class));
    }

    /**
     * 测试：创建审批实例时模板不存在
     * 验证：抛出 BizException
     */
    @Test
    void testCreateInstance_templateNotFound() {
        ApprovalCreateRequest request = new ApprovalCreateRequest();
        request.setTemplateId(999L);
        request.setBusinessType("METRIC_PUBLISH");
        request.setBusinessId("metric-100");
        request.setTitle("Test");
        request.setInitiator("user1");

        // 模拟模板不存在
        when(templateMapper.selectById(999L)).thenReturn(null);

        // 执行并验证异常
        assertThrows(BizException.class, () -> approvalService.createInstance(request));
    }

    /**
     * 测试：单步审批通过
     * 验证：步骤状态变为 approved，实例状态变为 approved
     */
    @Test
    void testApprove_singleStep_approved() {
        // 模拟实例查询：第一次返回 running，第二次返回 approved
        ApprovalInstance completedInstance = new ApprovalInstance();
        completedInstance.setId(1L);
        completedInstance.setTemplateId(1L);
        completedInstance.setBusinessType("METRIC_PUBLISH");
        completedInstance.setBusinessId("metric-001");
        completedInstance.setTitle("Test");
        completedInstance.setInitiator("creator");
        completedInstance.setStatus("approved");
        completedInstance.setResultNote("审批通过");
        completedInstance.setCurrentStep(1);

        doReturn(sampleInstance).doReturn(completedInstance).when(instanceMapper).selectById(1L);

        // 模拟步骤查询
        ApprovalStep runningStep = new ApprovalStep();
        runningStep.setId(1L);
        runningStep.setInstanceId(1L);
        runningStep.setStepOrder(1);
        runningStep.setStatus("running");
        runningStep.setApproverUser("admin");
        when(stepMapper.selectById(1L)).thenReturn(runningStep);

        // 模拟没有下一步（单步审批）
        when(stepMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(null);

        when(stepMapper.selectList(any(LambdaQueryWrapper.class))).thenReturn(List.of());

        // 准备请求
        ApprovalActionRequest actionRequest = new ApprovalActionRequest();
        actionRequest.setApprover("admin");
        actionRequest.setComment("Looks good");

        // 执行
        ApprovalInstanceDTO result = approvalService.approve(1L, 1L, actionRequest);

        // 验证
        assertNotNull(result);
        assertEquals("approved", result.getStatus());
        assertEquals("审批通过", result.getResultNote());
        verify(stepMapper).updateById(any(ApprovalStep.class));
        verify(instanceMapper).updateById(any(ApprovalInstance.class));
    }

    /**
     * 测试：多步审批 - 通过后进入下一步
     * 验证：当前步骤 approved，下一步变为 running
     */
    @Test
    void testApprove_multiStep_advanceToNext() {
        // 模拟实例查询：第一次返回 sampleInstance (running)，第二次返回 runningInstance
        ApprovalInstance runningInstance = new ApprovalInstance();
        runningInstance.setId(1L);
        runningInstance.setTemplateId(1L);
        runningInstance.setBusinessType("METRIC_PUBLISH");
        runningInstance.setBusinessId("metric-001");
        runningInstance.setTitle("Test");
        runningInstance.setInitiator("creator");
        runningInstance.setStatus("running");
        runningInstance.setCurrentStep(2);

        doReturn(sampleInstance).doReturn(runningInstance).when(instanceMapper).selectById(1L);

        // 模拟当前步骤
        when(stepMapper.selectById(1L)).thenReturn(sampleStep);

        // 模拟存在下一步
        ApprovalStep nextStep = new ApprovalStep();
        nextStep.setId(2L);
        nextStep.setInstanceId(1L);
        nextStep.setStepOrder(2);
        nextStep.setStatus("pending");
        nextStep.setApproverRole("data_owner");
        nextStep.setApproverUser("manager");
        when(stepMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(nextStep);

        ApprovalStep updatedStep1 = new ApprovalStep();
        updatedStep1.setId(1L);
        updatedStep1.setInstanceId(1L);
        updatedStep1.setStepOrder(1);
        updatedStep1.setStatus("approved");
        ApprovalStep updatedStep2 = new ApprovalStep();
        updatedStep2.setId(2L);
        updatedStep2.setInstanceId(1L);
        updatedStep2.setStepOrder(2);
        updatedStep2.setStatus("running");
        when(stepMapper.selectList(any(LambdaQueryWrapper.class)))
                .thenReturn(List.of(updatedStep1, updatedStep2));

        // 准备请求
        ApprovalActionRequest actionRequest = new ApprovalActionRequest();
        actionRequest.setApprover("admin");
        actionRequest.setComment("Step 1 passed");

        // 执行
        ApprovalInstanceDTO result = approvalService.approve(1L, 1L, actionRequest);

        // 验证
        assertNotNull(result);
        assertEquals("running", result.getStatus());
        assertEquals(2, result.getCurrentStep());
        verify(stepMapper).updateById((ApprovalStep) argThat(step ->
                ((ApprovalStep) step).getId().equals(1L) && "approved".equals(((ApprovalStep) step).getStatus())));
        verify(stepMapper).updateById((ApprovalStep) argThat(step ->
                ((ApprovalStep) step).getId().equals(2L) && "running".equals(((ApprovalStep) step).getStatus())));
    }

    /**
     * 测试：多步审批 - 最后一步通过后完成
     * 验证：所有步骤通过后，实例状态变为 approved
     */
    @Test
    void testApprove_finalStep_completed() {
        // 模拟实例查询：第一次返回 running，第二次返回 approved
        ApprovalInstance runningInstance = new ApprovalInstance();
        runningInstance.setId(1L);
        runningInstance.setTemplateId(1L);
        runningInstance.setBusinessType("METRIC_PUBLISH");
        runningInstance.setBusinessId("metric-001");
        runningInstance.setTitle("Test");
        runningInstance.setInitiator("creator");
        runningInstance.setStatus("running");
        runningInstance.setCurrentStep(2);

        ApprovalInstance completedInstance = new ApprovalInstance();
        completedInstance.setId(1L);
        completedInstance.setTemplateId(1L);
        completedInstance.setBusinessType("METRIC_PUBLISH");
        completedInstance.setBusinessId("metric-001");
        completedInstance.setTitle("Test");
        completedInstance.setInitiator("creator");
        completedInstance.setStatus("approved");
        completedInstance.setResultNote("审批通过");
        completedInstance.setCurrentStep(2);

        doReturn(runningInstance).doReturn(completedInstance).when(instanceMapper).selectById(1L);

        // 模拟最后一步
        ApprovalStep finalStep = new ApprovalStep();
        finalStep.setId(2L);
        finalStep.setInstanceId(1L);
        finalStep.setStepOrder(2);
        finalStep.setStatus("running");
        finalStep.setApproverUser("manager");
        when(stepMapper.selectById(2L)).thenReturn(finalStep);

        // 模拟没有更多步骤（reject路径不使用，approve路径使用）
        when(stepMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(null);
        when(stepMapper.selectList(any(LambdaQueryWrapper.class))).thenReturn(List.of());

        // 准备请求
        ApprovalActionRequest actionRequest = new ApprovalActionRequest();
        actionRequest.setApprover("manager");
        actionRequest.setComment("Final approval");

        // 执行
        ApprovalInstanceDTO result = approvalService.approve(1L, 2L, actionRequest);

        // 验证
        assertNotNull(result);
        assertEquals("approved", result.getStatus());
        assertEquals("审批通过", result.getResultNote());
    }

    /**
     * 测试：任意步骤驳回
     * 验证：实例状态变为 rejected，记录驳回原因
     */
    @Test
    void testReject_atAnyStep_rejected() {
        // 模拟实例查询：第一次返回 running，第二次返回 rejected
        ApprovalInstance rejectedInstance = new ApprovalInstance();
        rejectedInstance.setId(1L);
        rejectedInstance.setTemplateId(1L);
        rejectedInstance.setBusinessType("METRIC_PUBLISH");
        rejectedInstance.setBusinessId("metric-001");
        rejectedInstance.setTitle("Test");
        rejectedInstance.setInitiator("creator");
        rejectedInstance.setStatus("rejected");
        rejectedInstance.setResultNote("已驳回: 数据有误");
        rejectedInstance.setCurrentStep(1);

        doReturn(sampleInstance).doReturn(rejectedInstance).when(instanceMapper).selectById(1L);

        // 模拟步骤查询
        when(stepMapper.selectById(1L)).thenReturn(sampleStep);

        // getInstanceById 需要查询步骤列表
        when(stepMapper.selectList(any(LambdaQueryWrapper.class))).thenReturn(List.of());

        // 准备请求
        ApprovalActionRequest actionRequest = new ApprovalActionRequest();
        actionRequest.setApprover("admin");
        actionRequest.setComment("数据有误");

        // 执行
        ApprovalInstanceDTO result = approvalService.reject(1L, 1L, actionRequest);

        // 验证
        assertNotNull(result);
        assertEquals("rejected", result.getStatus());
        assertTrue(result.getResultNote().contains("已驳回"));
        verify(stepMapper).updateById((ApprovalStep) argThat(step ->
                ((ApprovalStep) step).getId().equals(1L) && "rejected".equals(((ApprovalStep) step).getStatus())));
    }

    /**
     * 测试：对已完成的审批实例再次操作
     * 验证：抛出 BizException (APPROVAL_ALREADY_COMPLETED)
     */
    @Test
    void testApprove_alreadyCompleted() {
        // 模拟已完成的实例
        ApprovalInstance completedInstance = new ApprovalInstance();
        completedInstance.setId(1L);
        completedInstance.setTemplateId(1L);
        completedInstance.setBusinessType("METRIC_PUBLISH");
        completedInstance.setBusinessId("metric-001");
        completedInstance.setTitle("Test");
        completedInstance.setInitiator("creator");
        completedInstance.setStatus("approved"); // 已完成
        when(instanceMapper.selectById(1L)).thenReturn(completedInstance);

        ApprovalActionRequest actionRequest = new ApprovalActionRequest();
        actionRequest.setApprover("admin");

        // 执行并验证异常
        assertThrows(BizException.class,
                () -> approvalService.approve(1L, 1L, actionRequest));
    }

    /**
     * 测试：审批步骤不属于该实例
     * 验证：抛出 BizException (APPROVAL_STEP_NOT_FOUND)
     */
    @Test
    void testApprove_wrongStep() {
        // 模拟实例查询
        when(instanceMapper.selectById(1L)).thenReturn(sampleInstance);

        // 模拟步骤属于另一个实例
        ApprovalStep wrongStep = new ApprovalStep();
        wrongStep.setId(99L);
        wrongStep.setInstanceId(2L); // 不属于 instanceId=1
        wrongStep.setStatus("running");
        when(stepMapper.selectById(99L)).thenReturn(wrongStep);

        ApprovalActionRequest actionRequest = new ApprovalActionRequest();
        actionRequest.setApprover("admin");

        // 执行并验证异常
        assertThrows(BizException.class,
                () -> approvalService.approve(1L, 99L, actionRequest));
    }

    /**
     * 测试：审批步骤状态不是 running
     * 验证：抛出 BizException (APPROVAL_NOT_YOUR_TURN)
     */
    @Test
    void testApprove_stepNotRunning() {
        // 模拟实例查询
        when(instanceMapper.selectById(1L)).thenReturn(sampleInstance);

        // 模拟步骤状态为 pending（非 running）
        ApprovalStep pendingStep = new ApprovalStep();
        pendingStep.setId(1L);
        pendingStep.setInstanceId(1L);
        pendingStep.setStepOrder(1);
        pendingStep.setStatus("pending");
        when(stepMapper.selectById(1L)).thenReturn(pendingStep);

        ApprovalActionRequest actionRequest = new ApprovalActionRequest();
        actionRequest.setApprover("admin");

        // 执行并验证异常
        assertThrows(BizException.class,
                () -> approvalService.approve(1L, 1L, actionRequest));
    }

    /**
     * 测试：获取我的待审批列表
     * 验证：返回当前用户待审批的实例列表
     */
    @Test
    void testGetMyTodoList() {
        // 模拟步骤查询 - 找到待审批步骤
        ApprovalStep myStep = new ApprovalStep();
        myStep.setId(1L);
        myStep.setInstanceId(1L);
        myStep.setStatus("running");
        myStep.setApproverUser("admin");
        when(stepMapper.selectList(any(LambdaQueryWrapper.class))).thenReturn(List.of(myStep));

        // 模拟实例查询
        when(instanceMapper.selectList(any(LambdaQueryWrapper.class)))
                .thenReturn(List.of(sampleInstance));

        // 执行
        List<ApprovalInstanceDTO> result = approvalService.getMyTodoList("admin");

        // 验证
        assertEquals(1, result.size());
        assertEquals("running", result.get(0).getStatus());
    }

    /**
     * 测试：按状态和发起人过滤审批实例列表
     * 验证：listInstances 方法正确应用过滤条件
     */
    @Test
    void testListInstances_byStatus() {
        // 模拟查询结果
        when(instanceMapper.selectList(any(LambdaQueryWrapper.class)))
                .thenReturn(List.of(sampleInstance));

        // 执行 - 按状态过滤
        List<ApprovalInstanceDTO> result = approvalService.listInstances("running", null);

        // 验证
        assertEquals(1, result.size());
        assertEquals("running", result.get(0).getStatus());
        verify(instanceMapper).selectList(any(LambdaQueryWrapper.class));
    }

    /**
     * 测试：获取审批实例详情（包含步骤列表）
     * 验证：返回实例信息和关联的步骤列表
     */
    @Test
    void testGetInstanceById_withSteps() {
        // 模拟实例查询
        when(instanceMapper.selectById(1L)).thenReturn(sampleInstance);

        // 模拟步骤查询
        ApprovalStep step1 = new ApprovalStep();
        step1.setId(1L);
        step1.setInstanceId(1L);
        step1.setStepOrder(1);
        step1.setApproverRole("data_steward");
        step1.setApproverUser("admin");
        step1.setStatus("approved");
        step1.setAction("approve");
        step1.setComment("OK");
        step1.setDecidedAt(OffsetDateTime.now().minusHours(1));

        ApprovalStep step2 = new ApprovalStep();
        step2.setId(2L);
        step2.setInstanceId(1L);
        step2.setStepOrder(2);
        step2.setApproverRole("data_owner");
        step2.setApproverUser("manager");
        step2.setStatus("running");

        when(stepMapper.selectList(any(LambdaQueryWrapper.class)))
                .thenReturn(List.of(step1, step2));

        // 执行
        ApprovalInstanceDTO result = approvalService.getInstanceById(1L);

        // 验证
        assertNotNull(result);
        assertEquals("running", result.getStatus());
        assertNotNull(result.getSteps());
        assertEquals(2, result.getSteps().size());
        assertEquals("approved", result.getSteps().get(0).getStatus());
        assertEquals("running", result.getSteps().get(1).getStatus());
    }

    /**
     * 测试：按业务类型过滤审批模板列表
     * 验证：listTemplates 方法正确应用 businessType 和 isActive 过滤
     */
    @Test
    void testListTemplates_filterByBusinessType() {
        // 模拟查询结果
        when(templateMapper.selectList(any(LambdaQueryWrapper.class)))
                .thenReturn(List.of(sampleTemplate));

        // 执行
        List<ApprovalTemplateDTO> result = approvalService.listTemplates("METRIC_PUBLISH", true);

        // 验证
        assertEquals(1, result.size());
        assertEquals("METRIC_PUBLISH", result.get(0).getBusinessType());
        assertTrue(result.get(0).getIsActive());
        verify(templateMapper).selectList(any(LambdaQueryWrapper.class));
    }
}
