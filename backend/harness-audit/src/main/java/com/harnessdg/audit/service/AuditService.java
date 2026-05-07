/**
 * 功能：审计日志服务接口
 * 时间：2026-05-07
 * 作者：AxeXie
 */
package com.harnessdg.audit.service;

import com.harnessdg.common.page.PageRequest;
import com.harnessdg.common.page.PageResult;
import com.harnessdg.model.audit.dto.AuditLogDTO;
import com.harnessdg.model.audit.dto.AuditLogQuery;

import java.util.List;

public interface AuditService {

    /** 异步写入审计日志 */
    void log(String action, String resourceType, String resourceId, String detail);

    /** 分页查询审计日志 */
    PageResult<AuditLogDTO> queryLogs(AuditLogQuery query, PageRequest pageRequest);

    /** 查询单条审计日志 */
    AuditLogDTO getLog(Long id);

    /** 按 traceId 查询链路上所有日志（按时间升序） */
    List<AuditLogDTO> getTrace(String traceId);
}
