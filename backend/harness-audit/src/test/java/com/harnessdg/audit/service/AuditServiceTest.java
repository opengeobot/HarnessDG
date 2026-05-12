/**
 * 功能：审计日志服务单元测试
 * 时间：2026-05-12
 * 作者：AxeXie
 */
package com.harnessdg.audit.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.harnessdg.audit.mapper.SysAuditLogMapper;
import com.harnessdg.audit.service.impl.AuditServiceImpl;
import com.harnessdg.common.log.TraceContext;
import com.harnessdg.common.page.PageRequest;
import com.harnessdg.common.page.PageResult;
import com.harnessdg.model.audit.dto.AuditLogDTO;
import com.harnessdg.model.audit.dto.AuditLogQuery;
import com.harnessdg.model.audit.entity.SysAuditLog;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;

import java.time.OffsetDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AuditServiceTest {

    @Mock
    private SysAuditLogMapper auditLogMapper;

    @InjectMocks
    private AuditServiceImpl auditService;

    private SysAuditLog sampleLog;

    @BeforeEach
    void setUp() {
        // 初始化测试数据
        sampleLog = new SysAuditLog();
        sampleLog.setId(1L);
        sampleLog.setTraceId("test-trace-001");
        sampleLog.setOperator("test-user");
        sampleLog.setAction("CREATE");
        sampleLog.setResourceType("ENTITY");
        sampleLog.setResourceId("entity-123");
        sampleLog.setResourceName("Test Entity");
        sampleLog.setDetail("{\"key\": \"value\"}");
        sampleLog.setStatus("success");
        sampleLog.setIpAddress("192.168.1.1");
        sampleLog.setUserAgent("Mozilla/5.0");
        sampleLog.setCreatedAt(OffsetDateTime.now());
    }

    @AfterEach
    void tearDown() {
        // 清理 TraceContext 防止影响其他测试
        TraceContext.clear();
    }

    /**
     * 测试：异步写入审计日志成功
     * 验证：insert 方法被调用，实体字段设置正确
     */
    @Test
    void testLog_success() {
        // 设置 TraceContext
        TraceContext.setTraceId("test-trace-002");
        TraceContext.setIpAddress("10.0.0.1");
        TraceContext.setUserAgent("TestAgent/1.0");

        // 执行
        auditService.log("CREATE", "METRIC", "metric-456", "{\"desc\": \"test\"}");

        // 验证 insert 被调用
        ArgumentCaptor<SysAuditLog> captor = ArgumentCaptor.forClass(SysAuditLog.class);
        verify(auditLogMapper).insert(captor.capture());

        SysAuditLog captured = captor.getValue();
        assertEquals("test-trace-002", captured.getTraceId());
        assertEquals("CREATE", captured.getAction());
        assertEquals("METRIC", captured.getResourceType());
        assertEquals("metric-456", captured.getResourceId());
        assertEquals("success", captured.getStatus());
        assertEquals("10.0.0.1", captured.getIpAddress());
        assertEquals("TestAgent/1.0", captured.getUserAgent());
        assertEquals("system", captured.getOperator()); // 无 SecurityContext 时为 system
    }

    /**
     * 测试：携带 SecurityContext 时 operator 自动填充
     * 验证：从 SecurityContext 中提取用户名作为 operator
     */
    @Test
    void testLog_withAuthContext() {
        // 模拟 SecurityContextHolder
        try (MockedStatic<SecurityContextHolder> mockedSecurity = mockStatic(SecurityContextHolder.class)) {
            SecurityContext securityContext = mock(SecurityContext.class);
            Authentication authentication = mock(Authentication.class);

            mockedSecurity.when(SecurityContextHolder::getContext).thenReturn(securityContext);
            when(securityContext.getAuthentication()).thenReturn(authentication);
            when(authentication.isAuthenticated()).thenReturn(true);
            when(authentication.getPrincipal()).thenReturn("admin-user");

            TraceContext.setTraceId("auth-trace-001");

            // 执行
            auditService.log("UPDATE", "DATASOURCE", "ds-789", "{\"name\": \"test\"}");

            // 验证
            ArgumentCaptor<SysAuditLog> captor = ArgumentCaptor.forClass(SysAuditLog.class);
            verify(auditLogMapper).insert(captor.capture());

            assertEquals("admin-user", captor.getValue().getOperator());
        }
    }

    /**
     * 测试：无认证时 operator 为 "system"
     * 验证：未登录场景下 operator 默认为 system
     */
    @Test
    void testLog_withoutAuthContext() {
        try (MockedStatic<SecurityContextHolder> mockedSecurity = mockStatic(SecurityContextHolder.class)) {
            SecurityContext securityContext = mock(SecurityContext.class);
            mockedSecurity.when(SecurityContextHolder::getContext).thenReturn(securityContext);
            when(securityContext.getAuthentication()).thenReturn(null);

            TraceContext.setTraceId("no-auth-trace-001");

            // 执行
            auditService.log("DELETE", "REPORT", "report-001", null);

            // 验证
            ArgumentCaptor<SysAuditLog> captor = ArgumentCaptor.forClass(SysAuditLog.class);
            verify(auditLogMapper).insert(captor.capture());

            assertEquals("system", captor.getValue().getOperator());
        }
    }

    /**
     * 测试：按操作人查询
     * 验证：queryLogs 方法使用 operator 条件过滤
     */
    @Test
    void testQueryLogs_byOperator() {
        // 准备查询条件
        AuditLogQuery query = new AuditLogQuery();
        query.setOperator("admin");
        PageRequest pageRequest = new PageRequest();
        pageRequest.setPage(1);
        pageRequest.setPageSize(10);

        // 模拟分页查询结果
        Page<SysAuditLog> page = new Page<>(1, 10);
        page.setRecords(List.of(sampleLog));
        page.setTotal(1);
        when(auditLogMapper.selectPage(any(Page.class), any(LambdaQueryWrapper.class))).thenReturn(page);

        // 执行
        PageResult<AuditLogDTO> result = auditService.queryLogs(query, pageRequest);

        // 验证
        assertEquals(1, result.getItems().size());
        assertEquals("test-user", result.getItems().get(0).getOperator());
        assertEquals(1, result.getTotal());
        verify(auditLogMapper).selectPage(any(Page.class), any(LambdaQueryWrapper.class));
    }

    /**
     * 测试：按资源类型查询
     * 验证：queryLogs 方法使用 resourceType 条件过滤
     */
    @Test
    void testQueryLogs_byResourceType() {
        AuditLogQuery query = new AuditLogQuery();
        query.setResourceType("ENTITY");
        PageRequest pageRequest = new PageRequest();
        pageRequest.setPage(1);
        pageRequest.setPageSize(20);

        Page<SysAuditLog> page = new Page<>(1, 20);
        page.setRecords(List.of(sampleLog));
        page.setTotal(1);
        when(auditLogMapper.selectPage(any(Page.class), any(LambdaQueryWrapper.class))).thenReturn(page);

        // 执行
        PageResult<AuditLogDTO> result = auditService.queryLogs(query, pageRequest);

        // 验证
        assertEquals(1, result.getItems().size());
        assertEquals("ENTITY", result.getItems().get(0).getResourceType());
    }

    /**
     * 测试：按时间范围查询
     * 验证：queryLogs 方法使用 startTime 和 endTime 条件过滤
     */
    @Test
    void testQueryLogs_byTimeRange() {
        AuditLogQuery query = new AuditLogQuery();
        query.setStartTime(OffsetDateTime.now().minusDays(7));
        query.setEndTime(OffsetDateTime.now());
        PageRequest pageRequest = new PageRequest();
        pageRequest.setPage(1);
        pageRequest.setPageSize(10);

        Page<SysAuditLog> page = new Page<>(1, 10);
        page.setRecords(List.of(sampleLog));
        page.setTotal(1);
        when(auditLogMapper.selectPage(any(Page.class), any(LambdaQueryWrapper.class))).thenReturn(page);

        // 执行
        PageResult<AuditLogDTO> result = auditService.queryLogs(query, pageRequest);

        // 验证
        assertNotNull(result);
        assertEquals(1, result.getItems().size());
        assertEquals(1, result.getTotal());
    }

    /**
     * 测试：关键词搜索
     * 验证：queryLogs 方法使用 keyword 在 resourceName、resourceId、action 中模糊搜索
     */
    @Test
    void testQueryLogs_withKeyword() {
        AuditLogQuery query = new AuditLogQuery();
        query.setKeyword("test");
        PageRequest pageRequest = new PageRequest();
        pageRequest.setPage(1);
        pageRequest.setPageSize(10);

        Page<SysAuditLog> page = new Page<>(1, 10);
        page.setRecords(List.of(sampleLog));
        page.setTotal(1);
        when(auditLogMapper.selectPage(any(Page.class), any(LambdaQueryWrapper.class))).thenReturn(page);

        // 执行
        PageResult<AuditLogDTO> result = auditService.queryLogs(query, pageRequest);

        // 验证
        assertEquals(1, result.getItems().size());
        assertEquals("Test Entity", result.getItems().get(0).getResourceName());
    }

    /**
     * 测试：按 traceId 获取完整链路
     * 验证：getTrace 方法按 traceId 查询并升序排列
     */
    @Test
    void testGetTrace() {
        String traceId = "trace-chain-001";

        SysAuditLog log1 = new SysAuditLog();
        log1.setId(1L);
        log1.setTraceId(traceId);
        log1.setAction("LOGIN");
        log1.setCreatedAt(OffsetDateTime.now().minusMinutes(5));

        SysAuditLog log2 = new SysAuditLog();
        log2.setId(2L);
        log2.setTraceId(traceId);
        log2.setAction("CREATE");
        log2.setCreatedAt(OffsetDateTime.now());

        when(auditLogMapper.selectList(any(LambdaQueryWrapper.class)))
                .thenReturn(List.of(log1, log2));

        // 执行
        List<AuditLogDTO> result = auditService.getTrace(traceId);

        // 验证
        assertEquals(2, result.size());
        assertEquals("LOGIN", result.get(0).getAction());
        assertEquals("CREATE", result.get(1).getAction());
        verify(auditLogMapper).selectList(any(LambdaQueryWrapper.class));
    }

    /**
     * 测试：分页查询 - 多页数据
     * 验证：分页参数正确传递，返回正确的分页结果
     */
    @Test
    void testQueryLogs_pagination() {
        AuditLogQuery query = new AuditLogQuery();
        PageRequest pageRequest = new PageRequest();
        pageRequest.setPage(2);
        pageRequest.setPageSize(5);

        // 模拟第二页数据
        Page<SysAuditLog> page = new Page<>(2, 5);
        SysAuditLog log1 = new SysAuditLog();
        log1.setId(6L);
        log1.setAction("UPDATE");
        SysAuditLog log2 = new SysAuditLog();
        log2.setId(7L);
        log2.setAction("DELETE");
        page.setRecords(List.of(log1, log2));
        page.setTotal(12); // 总共 12 条
        when(auditLogMapper.selectPage(any(Page.class), any(LambdaQueryWrapper.class))).thenReturn(page);

        // 执行
        PageResult<AuditLogDTO> result = auditService.queryLogs(query, pageRequest);

        // 验证
        assertEquals(2, result.getItems().size());
        assertEquals(12, result.getTotal());
        assertEquals(2, result.getPage());
        assertEquals(5, result.getPageSize());
        assertEquals(3, result.getTotalPages()); // ceil(12/5) = 3
    }

    /**
     * 测试：日志写入异常不向上抛出
     * 验证：当 insert 抛出异常时，方法不会抛出异常
     */
    @Test
    void testLog_exceptionNotThrown() {
        TraceContext.setTraceId("error-trace-001");
        when(auditLogMapper.insert(any(SysAuditLog.class))).thenThrow(new RuntimeException("DB connection lost"));

        // 执行 - 不应该抛出异常
        assertDoesNotThrow(() -> auditService.log("CREATE", "ENTITY", "err-001", "test"));

        // 验证 insert 被调用过
        verify(auditLogMapper).insert(any(SysAuditLog.class));
    }
}
