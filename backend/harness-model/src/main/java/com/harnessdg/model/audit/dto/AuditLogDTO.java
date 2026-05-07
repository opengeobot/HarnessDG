/**
 * 功能：审计日志展示对象
 * 时间：2026-05-07
 * 作者：AxeXie
 */
package com.harnessdg.model.audit.dto;

import lombok.Builder;
import lombok.Data;

import java.time.OffsetDateTime;

@Data
@Builder
public class AuditLogDTO {

    private Long id;

    private String traceId;

    private Long taskId;

    private String operator;

    private String action;

    private String resourceType;

    private String resourceId;

    private String resourceName;

    private Object detail;

    private String agentSessionId;

    private String ipAddress;

    private String userAgent;

    private String status;

    private Long durationMs;

    private OffsetDateTime createdAt;
}
