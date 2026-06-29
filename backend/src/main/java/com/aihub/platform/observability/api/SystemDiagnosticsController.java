/*
 * 功能: 系统诊断 REST 适配器，暴露只读的系统依赖与健康摘要接口。
 * 时间: 2026-06-29
 * 作者: AxeXie
 */
package com.aihub.platform.observability.api;

import com.aihub.platform.observability.application.SystemDependencyService;
import com.aihub.platform.observability.domain.SystemDependencySummary;
import com.aihub.shared.api.ApiResponse;
import com.aihub.shared.identity.PrincipalContext;
import com.aihub.shared.identity.PrincipalContextHolder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 系统诊断 REST 适配器。
 *
 * <p>实现 OpenAPI 契约 {@code GET /api/v1/system/dependencies}，作为 P0 唯一只读示例路径，
 * 演示"适配器 → 应用服务"的边界：Controller 不含业务规则、不直接访问 Mapper，
 * 仅编排应用服务并以统一 {@link ApiResponse} 包装返回。requestId/traceId 来自入口建立的请求上下文。
 */
@RestController
@RequestMapping("/api/v1/system")
public class SystemDiagnosticsController {

    private final SystemDependencyService systemDependencyService;

    public SystemDiagnosticsController(SystemDependencyService systemDependencyService) {
        this.systemDependencyService = systemDependencyService;
    }

    /**
     * 获取系统依赖与健康摘要（运维诊断）。
     *
     * @return 统一成功响应包装的依赖摘要
     */
    @GetMapping("/dependencies")
    public ApiResponse<SystemDependencySummary> getSystemDependencies() {
        SystemDependencySummary summary = systemDependencyService.summarize();
        return ApiResponse.of(summary, currentRequestId(), currentTraceId());
    }

    private String currentRequestId() {
        return PrincipalContextHolder.current().map(PrincipalContext::requestId).orElse(null);
    }

    private String currentTraceId() {
        return PrincipalContextHolder.current().map(PrincipalContext::traceId).orElse(null);
    }
}
