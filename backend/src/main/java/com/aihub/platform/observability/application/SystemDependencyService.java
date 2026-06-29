/*
 * 功能: 系统依赖摘要应用服务，聚合所有依赖探测结果。
 * 时间: 2026-06-29
 * 作者: AxeXie
 */
package com.aihub.platform.observability.application;

import com.aihub.platform.observability.domain.DependencyHealthProbe;
import com.aihub.platform.observability.domain.DependencyStatus;
import com.aihub.platform.observability.domain.SystemDependencySummary;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import org.springframework.stereotype.Service;

/**
 * 系统依赖摘要应用服务。
 *
 * <p>聚合所有已注册的 {@link DependencyHealthProbe}，输出 {@link SystemDependencySummary}。
 * 作为应用层，只编排领域端口，不感知具体依赖的探测细节与基础设施 SDK。
 */
@Service
public class SystemDependencyService {

    private final List<DependencyHealthProbe> probes;

    public SystemDependencyService(List<DependencyHealthProbe> probes) {
        this.probes = probes;
    }

    /**
     * 探测全部依赖并聚合摘要。结果按依赖名称排序，保证响应稳定。
     *
     * @return 系统依赖与健康摘要
     */
    public SystemDependencySummary summarize() {
        List<DependencyStatus> statuses = new ArrayList<>(probes.size());
        for (DependencyHealthProbe probe : probes) {
            statuses.add(probe.probe());
        }
        statuses.sort(Comparator.comparing(DependencyStatus::name));
        return SystemDependencySummary.aggregate(statuses);
    }
}
