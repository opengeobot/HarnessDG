package com.harnessdg.model.task.dto;

import lombok.Data;

import java.time.OffsetDateTime;
import java.util.Map;

@Data
public class TaskDTO {

    private Long id;
    private String title;
    private String taskType;
    private String status;
    private String priority;
    private String assignee;
    private String traceId;
    private String agentSessionId;
    private Map<String, Object> inputPayload;
    private Map<String, Object> outputPayload;
    private String errorMessage;
    private OffsetDateTime startedAt;
    private OffsetDateTime completedAt;
    private OffsetDateTime createdAt;
}
