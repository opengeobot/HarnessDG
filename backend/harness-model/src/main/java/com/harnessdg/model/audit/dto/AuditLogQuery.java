/**
 * 功能：审计日志查询条件
 * 时间：2026-05-07
 * 作者：AxeXie
 */
package com.harnessdg.model.audit.dto;

import lombok.Data;

import java.time.OffsetDateTime;

@Data
public class AuditLogQuery {

    private String operator;
    private String resourceType;
    private String resourceId;
    private String action;
    private String status;
    private String traceId;
    private String keyword;
    private OffsetDateTime startTime;
    private OffsetDateTime endTime;
}
