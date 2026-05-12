/**
 * 功能：审计日志服务实现（异步写入 + 多条件查询）
 * 时间：2026-05-07
 * 作者：AxeXie
 */
package com.harnessdg.audit.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.harnessdg.audit.mapper.SysAuditLogMapper;
import com.harnessdg.audit.service.AuditService;
import com.harnessdg.common.log.TraceContext;
import com.harnessdg.common.page.PageRequest;
import com.harnessdg.common.page.PageResult;
import com.harnessdg.model.audit.dto.AuditLogDTO;
import com.harnessdg.model.audit.dto.AuditLogQuery;
import com.harnessdg.model.audit.entity.SysAuditLog;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.OffsetDateTime;
import java.util.List;

@Service
@Slf4j
@RequiredArgsConstructor
public class AuditServiceImpl implements AuditService {

    private final SysAuditLogMapper auditLogMapper;

    @Override
    @Async
    public void log(String action, String resourceType, String resourceId, String detail) {
        try {
            SysAuditLog entity = new SysAuditLog();
            String traceId = TraceContext.getTraceId();
            entity.setTraceId(StringUtils.hasText(traceId) ? traceId : TraceContext.generateTraceId());
            entity.setAction(action);
            entity.setResourceType(resourceType);
            entity.setResourceId(resourceId);
            entity.setDetail(detail);
            entity.setStatus("success");
            entity.setCreatedAt(OffsetDateTime.now());

            // 填充 IP 地址
            String ipAddress = TraceContext.getIpAddress();
            if (StringUtils.hasText(ipAddress)) {
                entity.setIpAddress(ipAddress);
            }

            // 填充 User-Agent
            String userAgent = TraceContext.getUserAgent();
            if (StringUtils.hasText(userAgent)) {
                entity.setUserAgent(userAgent);
            }

            Authentication auth = SecurityContextHolder.getContext().getAuthentication();
            if (auth != null && auth.isAuthenticated() && auth.getPrincipal() != null) {
                Object principal = auth.getPrincipal();
                if (principal instanceof String s && !"anonymousUser".equals(s)) {
                    entity.setOperator(s);
                }
            }
            if (!StringUtils.hasText(entity.getOperator())) {
                entity.setOperator("system");
            }

            auditLogMapper.insert(entity);
        } catch (Exception e) {
            log.warn("Failed to insert audit log: action={}, resourceType={}", action, resourceType, e);
        }
    }

    @Override
    public PageResult<AuditLogDTO> queryLogs(AuditLogQuery query, PageRequest pageRequest) {
        LambdaQueryWrapper<SysAuditLog> wrapper = new LambdaQueryWrapper<>();
        if (query != null) {
            if (StringUtils.hasText(query.getOperator())) {
                wrapper.like(SysAuditLog::getOperator, query.getOperator());
            }
            if (StringUtils.hasText(query.getResourceType())) {
                wrapper.eq(SysAuditLog::getResourceType, query.getResourceType());
            }
            if (StringUtils.hasText(query.getResourceId())) {
                wrapper.eq(SysAuditLog::getResourceId, query.getResourceId());
            }
            if (StringUtils.hasText(query.getAction())) {
                wrapper.eq(SysAuditLog::getAction, query.getAction());
            }
            if (StringUtils.hasText(query.getStatus())) {
                wrapper.eq(SysAuditLog::getStatus, query.getStatus());
            }
            if (StringUtils.hasText(query.getTraceId())) {
                wrapper.eq(SysAuditLog::getTraceId, query.getTraceId());
            }
            if (StringUtils.hasText(query.getKeyword())) {
                String kw = query.getKeyword();
                wrapper.and(w -> w.like(SysAuditLog::getResourceName, kw)
                        .or().like(SysAuditLog::getResourceId, kw)
                        .or().like(SysAuditLog::getAction, kw));
            }
            if (query.getStartTime() != null) {
                wrapper.ge(SysAuditLog::getCreatedAt, query.getStartTime());
            }
            if (query.getEndTime() != null) {
                wrapper.le(SysAuditLog::getCreatedAt, query.getEndTime());
            }
        }
        wrapper.orderByDesc(SysAuditLog::getCreatedAt);

        Page<SysAuditLog> page = new Page<>(pageRequest.getPage(), pageRequest.getPageSize());
        Page<SysAuditLog> result = auditLogMapper.selectPage(page, wrapper);

        List<AuditLogDTO> records = result.getRecords().stream()
                .map(this::toDTO)
                .toList();
        return PageResult.of(records, result.getTotal(), pageRequest.getPage(), pageRequest.getPageSize());
    }

    @Override
    public AuditLogDTO getLog(Long id) {
        SysAuditLog entity = auditLogMapper.selectById(id);
        return entity == null ? null : toDTO(entity);
    }

    @Override
    public List<AuditLogDTO> getTrace(String traceId) {
        LambdaQueryWrapper<SysAuditLog> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(SysAuditLog::getTraceId, traceId)
                .orderByAsc(SysAuditLog::getCreatedAt);
        return auditLogMapper.selectList(wrapper).stream()
                .map(this::toDTO)
                .toList();
    }

    private AuditLogDTO toDTO(SysAuditLog entity) {
        return AuditLogDTO.builder()
                .id(entity.getId())
                .traceId(entity.getTraceId())
                .taskId(entity.getTaskId())
                .operator(entity.getOperator())
                .action(entity.getAction())
                .resourceType(entity.getResourceType())
                .resourceId(entity.getResourceId())
                .resourceName(entity.getResourceName())
                .detail(entity.getDetail())
                .agentSessionId(entity.getAgentSessionId())
                .ipAddress(entity.getIpAddress())
                .userAgent(entity.getUserAgent())
                .status(entity.getStatus())
                .durationMs(entity.getDurationMs())
                .createdAt(entity.getCreatedAt())
                .build();
    }
}
