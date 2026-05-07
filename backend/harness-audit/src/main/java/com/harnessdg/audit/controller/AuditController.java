/**
 * 功能：审计日志 REST 控制器
 * 时间：2026-05-07
 * 作者：AxeXie
 */
package com.harnessdg.audit.controller;

import com.harnessdg.audit.service.AuditService;
import com.harnessdg.common.page.PageRequest;
import com.harnessdg.common.page.PageResult;
import com.harnessdg.common.response.R;
import com.harnessdg.model.audit.dto.AuditLogDTO;
import com.harnessdg.model.audit.dto.AuditLogQuery;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.time.OffsetDateTime;
import java.util.List;

@RestController
@RequestMapping("/api/audit")
@RequiredArgsConstructor
public class AuditController {

    private final AuditService auditService;

    @GetMapping("/logs")
    @PreAuthorize("hasRole('admin')")
    public R<PageResult<AuditLogDTO>> queryLogs(
            @RequestParam(required = false) String operator,
            @RequestParam(required = false) String resourceType,
            @RequestParam(required = false) String resourceId,
            @RequestParam(required = false) String action,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String traceId,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) OffsetDateTime startTime,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) OffsetDateTime endTime,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int pageSize) {
        AuditLogQuery query = new AuditLogQuery();
        query.setOperator(operator);
        query.setResourceType(resourceType);
        query.setResourceId(resourceId);
        query.setAction(action);
        query.setStatus(status);
        query.setTraceId(traceId);
        query.setKeyword(keyword);
        query.setStartTime(startTime);
        query.setEndTime(endTime);

        PageRequest pr = new PageRequest();
        pr.setPage(page);
        pr.setPageSize(pageSize);
        return R.ok(auditService.queryLogs(query, pr));
    }

    @GetMapping("/logs/{id}")
    @PreAuthorize("hasRole('admin')")
    public R<AuditLogDTO> getLog(@PathVariable Long id) {
        return R.ok(auditService.getLog(id));
    }

    @GetMapping("/trace/{traceId}")
    @PreAuthorize("hasRole('admin')")
    public R<List<AuditLogDTO>> getTrace(@PathVariable String traceId) {
        return R.ok(auditService.getTrace(traceId));
    }
}
