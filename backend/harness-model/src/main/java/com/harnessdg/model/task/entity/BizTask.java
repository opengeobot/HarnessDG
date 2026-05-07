package com.harnessdg.model.task.entity;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.extension.handlers.JacksonTypeHandler;
import com.harnessdg.common.base.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.OffsetDateTime;
import java.util.Map;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName(value = "biz_task", autoResultMap = true)
public class BizTask extends BaseEntity {

    private String title;

    private String taskType;

    private String status;

    private String priority;

    private String assignee;

    private String traceId;

    private String agentSessionId;

    private String dagsterRunId;

    @TableField(typeHandler = JacksonTypeHandler.class)
    private Map<String, Object> inputPayload;

    @TableField(typeHandler = JacksonTypeHandler.class)
    private Map<String, Object> outputPayload;

    private String errorMessage;

    private OffsetDateTime startedAt;

    private OffsetDateTime completedAt;
}
