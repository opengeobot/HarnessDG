package com.harnessdg.task.controller;

import com.harnessdg.common.page.PageRequest;
import com.harnessdg.common.page.PageResult;
import com.harnessdg.common.response.R;
import com.harnessdg.model.task.dto.TaskCreateRequest;
import com.harnessdg.model.task.dto.TaskDTO;
import com.harnessdg.task.service.TaskService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/tasks")
@RequiredArgsConstructor
public class TaskController {

    private final TaskService taskService;

    @GetMapping
    public R<PageResult<TaskDTO>> listTasks(
            @RequestParam(required = false) String taskType,
            @RequestParam(required = false) String status,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size) {
        PageRequest pageRequest = new PageRequest();
        pageRequest.setPage(page);
        pageRequest.setPageSize(size);
        return R.ok(taskService.listTasks(taskType, status, pageRequest));
    }

    @GetMapping("/{id}")
    public R<TaskDTO> getTask(@PathVariable Long id) {
        return R.ok(taskService.getTaskById(id));
    }

    @PostMapping
    public R<TaskDTO> createTask(@Valid @RequestBody TaskCreateRequest request) {
        return R.ok(taskService.createTask(request));
    }

    @PostMapping("/{id}/start")
    public R<TaskDTO> startTask(@PathVariable Long id) {
        return R.ok(taskService.startTask(id));
    }

    @PostMapping("/{id}/complete")
    public R<TaskDTO> completeTask(@PathVariable Long id, @RequestBody(required = false) Object output) {
        return R.ok(taskService.completeTask(id, output));
    }

    @PostMapping("/{id}/fail")
    public R<TaskDTO> failTask(@PathVariable Long id, @RequestBody java.util.Map<String, String> body) {
        return R.ok(taskService.failTask(id, body.get("errorMessage")));
    }
}
