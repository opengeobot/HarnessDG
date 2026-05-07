/**
 * 功能：审计日志实体，对齐 V005 sys_audit_log 表
 * 时间：2026-05-07
 * 作者：AxeXie
 */
package com.harnessdg.model.audit.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.extension.handlers.JacksonTypeHandler;
import lombok.Data;

import java.time.OffsetDateTime;

@Data
@TableName(value = "sys_audit_log", autoResultMap = true)
public class SysAuditLog {

    @TableId(type = IdType.AUTO)
    private Long id;

    private String traceId;

    private Long taskId;

    /** 操作人用户名 */
    private String operator;

    private String action;

    private String resourceType;

    private String resourceId;

    private String resourceName;

    @TableField(value = "detail", typeHandler = JacksonTypeHandler.class)
    private Object detail;

    private String agentSessionId;

    private String ipAddress;

    private String userAgent;

    /** success / failure */
    private String status;

    private Long durationMs;

    private OffsetDateTime createdAt;
}
