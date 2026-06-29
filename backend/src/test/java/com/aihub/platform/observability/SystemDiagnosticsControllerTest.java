/*
 * 功能: 系统诊断 Controller Web 切片测试——验证统一响应包装与请求上下文回填。
 * 时间: 2026-06-29
 * 作者: AxeXie
 */
package com.aihub.platform.observability;

import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.aihub.bootstrap.PrincipalContextFilter;
import com.aihub.bootstrap.SharedKernelConfiguration;
import com.aihub.platform.observability.api.SystemDiagnosticsController;
import com.aihub.platform.observability.application.SystemDependencyService;
import com.aihub.platform.observability.domain.DependencyHealth;
import com.aihub.platform.observability.domain.DependencyStatus;
import com.aihub.platform.observability.domain.SystemDependencySummary;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

/**
 * {@link SystemDiagnosticsController} Web 切片测试。
 *
 * <p>导入 {@link PrincipalContextFilter} 与 ID 生成器装配，验证：契约路径返回统一 {@code ApiResponse} 包装、
 * 业务 data 含依赖摘要、requestId 由入口上下文回填并与响应头一致。
 */
@WebMvcTest(SystemDiagnosticsController.class)
@Import({SystemDiagnosticsController.class, PrincipalContextFilter.class, SharedKernelConfiguration.class})
class SystemDiagnosticsControllerTest {

    /** 启动类位于 com.aihub.bootstrap 而非测试包祖先路径，故为切片测试提供本地配置锚点。 */
    @Configuration
    static class TestConfig {
    }

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private SystemDependencyService systemDependencyService;

    @Test
    void returnsUnifiedResponseWithDependencySummaryAndRequestId() throws Exception {
        given(systemDependencyService.summarize()).willReturn(new SystemDependencySummary(
                DependencyHealth.UP,
                List.of(new DependencyStatus("postgres", DependencyHealth.UP, 5L))));

        mockMvc.perform(get("/api/v1/system/dependencies")
                        .header(PrincipalContextFilter.REQUEST_ID_HEADER, "req_test_42"))
                .andExpect(status().isOk())
                .andExpect(header().string(PrincipalContextFilter.REQUEST_ID_HEADER, "req_test_42"))
                .andExpect(jsonPath("$.requestId").value("req_test_42"))
                .andExpect(jsonPath("$.traceId").isNotEmpty())
                .andExpect(jsonPath("$.timestamp").isNotEmpty())
                .andExpect(jsonPath("$.data.status").value("UP"))
                .andExpect(jsonPath("$.data.dependencies[0].name").value("postgres"))
                .andExpect(jsonPath("$.data.dependencies[0].status").value("UP"))
                .andExpect(jsonPath("$.data.dependencies[0].latencyMs").value(5));
    }
}
