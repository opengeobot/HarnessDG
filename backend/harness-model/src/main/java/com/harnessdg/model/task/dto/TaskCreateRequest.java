package com.harnessdg.model.task.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

import java.util.Map;

@Data
public class TaskCreateRequest {

    @NotBlank
    private String title;

    @NotBlank
    private String taskType;

    private String priority = "medium";

    private String assignee;

    private Map<String, Object> inputPayload;
}
