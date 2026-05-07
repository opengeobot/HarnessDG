package com.harnessdg.task.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.harnessdg.common.exception.BizException;
import com.harnessdg.common.log.TraceContext;
import com.harnessdg.common.page.PageRequest;
import com.harnessdg.common.page.PageResult;
import com.harnessdg.common.response.ErrorCode;
import com.harnessdg.model.task.dto.TaskCreateRequest;
import com.harnessdg.model.task.dto.TaskDTO;
import com.harnessdg.model.task.entity.BizTask;
import com.harnessdg.task.mapper.BizTaskMapper;
import com.harnessdg.task.service.TaskService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class TaskServiceImpl implements TaskService {

    private final BizTaskMapper taskMapper;

    @Override
    public PageResult<TaskDTO> listTasks(String taskType, String status, PageRequest pageRequest) {
        LambdaQueryWrapper<BizTask> wrapper = new LambdaQueryWrapper<>();
        if (taskType != null && !taskType.isBlank()) {
            wrapper.eq(BizTask::getTaskType, taskType);
        }
        if (status != null && !status.isBlank()) {
            wrapper.eq(BizTask::getStatus, status);
        }
        wrapper.orderByDesc(BizTask::getCreatedAt);

        Page<BizTask> page = new Page<>(pageRequest.getPage(), pageRequest.getPageSize());
        Page<BizTask> result = taskMapper.selectPage(page, wrapper);

        List<TaskDTO> records = result.getRecords().stream()
                .map(this::toDTO)
                .toList();
        return PageResult.of(records, result.getTotal(), pageRequest.getPage(), pageRequest.getPageSize());
    }

    @Override
    public TaskDTO getTaskById(Long id) {
        BizTask task = taskMapper.selectById(id);
        if (task == null) {
            throw new BizException(ErrorCode.TASK_NOT_FOUND);
        }
        return toDTO(task);
    }

    @Override
    @Transactional
    public TaskDTO createTask(TaskCreateRequest request) {
        BizTask task = new BizTask();
        task.setTitle(request.getTitle());
        task.setTaskType(request.getTaskType());
        task.setStatus("pending");
        task.setPriority(request.getPriority());
        task.setAssignee(request.getAssignee());
        task.setTraceId(TraceContext.getTraceId());
        task.setInputPayload(request.getInputPayload());
        taskMapper.insert(task);
        return toDTO(task);
    }

    @Override
    @Transactional
    public TaskDTO startTask(Long id) {
        BizTask task = taskMapper.selectById(id);
        if (task == null) {
            throw new BizException(ErrorCode.TASK_NOT_FOUND);
        }
        if (!"pending".equals(task.getStatus())) {
            throw new BizException(ErrorCode.TASK_CANNOT_EXECUTE);
        }
        task.setStatus("running");
        task.setStartedAt(OffsetDateTime.now());
        taskMapper.updateById(task);
        return toDTO(task);
    }

    @Override
    @Transactional
    @SuppressWarnings("unchecked")
    public TaskDTO completeTask(Long id, Object outputPayload) {
        BizTask task = taskMapper.selectById(id);
        if (task == null) {
            throw new BizException(ErrorCode.TASK_NOT_FOUND);
        }
        task.setStatus("completed");
        task.setCompletedAt(OffsetDateTime.now());
        if (outputPayload instanceof Map) {
            task.setOutputPayload((Map<String, Object>) outputPayload);
        }
        taskMapper.updateById(task);
        return toDTO(task);
    }

    @Override
    @Transactional
    public TaskDTO failTask(Long id, String errorMessage) {
        BizTask task = taskMapper.selectById(id);
        if (task == null) {
            throw new BizException(ErrorCode.TASK_NOT_FOUND);
        }
        task.setStatus("failed");
        task.setErrorMessage(errorMessage);
        task.setCompletedAt(OffsetDateTime.now());
        taskMapper.updateById(task);
        return toDTO(task);
    }

    private TaskDTO toDTO(BizTask task) {
        TaskDTO dto = new TaskDTO();
        dto.setId(task.getId());
        dto.setTitle(task.getTitle());
        dto.setTaskType(task.getTaskType());
        dto.setStatus(task.getStatus());
        dto.setPriority(task.getPriority());
        dto.setAssignee(task.getAssignee());
        dto.setTraceId(task.getTraceId());
        dto.setAgentSessionId(task.getAgentSessionId());
        dto.setInputPayload(task.getInputPayload());
        dto.setOutputPayload(task.getOutputPayload());
        dto.setErrorMessage(task.getErrorMessage());
        dto.setStartedAt(task.getStartedAt());
        dto.setCompletedAt(task.getCompletedAt());
        dto.setCreatedAt(task.getCreatedAt());
        return dto;
    }
}
