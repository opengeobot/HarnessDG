package com.harnessdg.task.service;

import com.harnessdg.common.page.PageRequest;
import com.harnessdg.common.page.PageResult;
import com.harnessdg.model.task.dto.TaskCreateRequest;
import com.harnessdg.model.task.dto.TaskDTO;

public interface TaskService {

    PageResult<TaskDTO> listTasks(String taskType, String status, PageRequest pageRequest);

    TaskDTO getTaskById(Long id);

    TaskDTO createTask(TaskCreateRequest request);

    TaskDTO startTask(Long id);

    TaskDTO completeTask(Long id, Object outputPayload);

    TaskDTO failTask(Long id, String errorMessage);
}
