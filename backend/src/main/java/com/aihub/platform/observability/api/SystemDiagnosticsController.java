/*
 * 功能: 系统诊断 REST 适配器，暴露受保护的指标摘要与依赖健康接口（system:observe 权限）。
 * 时间: 2026-06-29
 * 作者: AxeXie
 */
package com.aihub.platform.observability.api;

import com.aihub.authorization.application.AuthorizationService;
import com.aihub.authorization.domain.Permissions;
import com.aihub.platform.observability.application.MetricsSummaryService;
import com.aihub.platform.observability.application.SystemDependencyService;
import com.aihub.platform.observability.domain.MetricsSummary;
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
 * <p>实现 OpenAPI 契约 {@code GET /api/v1/system/metrics/summary} 与 {@code GET /api/v1/system/dependencies}。
 * 两端点均要求 {@code system:observe} 权限（fail-closed），非授权主体返回 403。
 * 适配层不含业务规则、不直接访问 Mapper，仅编排应用服务并以统一 {@link ApiResponse} 包装返回。
 */
@RestController
@RequestMapping("/api/v1/system")
public class SystemDiagnosticsController {

    private final SystemDependencyService systemDependencyService;
    private final MetricsSummaryService metricsSummaryService;
    private final AuthorizationService authorizationService;

    public SystemDiagnosticsController(SystemDependencyService systemDependencyService,
                                      MetricsSummaryService metricsSummaryService,
                                      AuthorizationService authorizationService) {
        this.systemDependencyService = systemDependencyService;
        this.metricsSummaryService = metricsSummaryService;
        this.authorizationService = authorizationService;
    }

    /**
     * 获取平台指标摘要（运维诊断）。
     *
     * <p>聚合 JVM/任务/依赖/API 指标，仅暴露非敏感运维信息。要求 {@code system:observe} 权限。
     *
     * @return 统一成功响应包装的指标摘要
     */
    @GetMapping("/metrics/summary")
    public ApiResponse<MetricsSummary> getMetricsSummary() {
        authorizationService.requirePermission(Permissions.SYSTEM_OBSERVE);
        MetricsSummary summary = metricsSummaryService.summarize();
        return ApiResponse.of(summary, currentRequestId(), currentTraceId());
    }

    /**
     * 获取系统依赖与健康摘要（运维诊断）。
     *
     * <p>返回平台关键依赖的精简健康摘要，不包含内部 Endpoint、凭据或网络拓扑。
     * 要求 {@code system:observe} 权限（fail-closed）。
     *
     * @return 统一成功响应包装的依赖摘要
     */
    @GetMapping("/dependencies")
    public ApiResponse<SystemDependencySummary> getSystemDependencies() {
        authorizationService.requirePermission(Permissions.SYSTEM_OBSERVE);
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
