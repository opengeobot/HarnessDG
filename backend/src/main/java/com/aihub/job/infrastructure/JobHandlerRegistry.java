/*
 * 功能: 任务处理器注册表，按 type 路由到对应幂等 JobHandler。
 * 时间: 2026-06-30
 * 作者: AxeXie
 */
package com.aihub.job.infrastructure;

import com.aihub.job.domain.JobHandler;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Component;

/**
 * 任务处理器注册表。
 *
 * <p>收集容器内所有 {@link JobHandler} Bean，按 {@link JobHandler#type()} 建立路由表。
 * 真实业务 Handler（DVC/发布/Webhook 消费/对账）属后续阶段，本任务提供框架 + 示例 Handler。
 */
@Component
public class JobHandlerRegistry {

    private final Map<String, JobHandler> handlers;

    public JobHandlerRegistry(List<JobHandler> handlers) {
        this.handlers = new HashMap<>();
        for (JobHandler handler : handlers) {
            this.handlers.put(handler.type(), handler);
        }
    }

    /**
     * 按类型解析处理器。
     *
     * @param type 任务类型
     * @return 处理器，未注册时为空
     */
    public java.util.Optional<JobHandler> resolve(String type) {
        return java.util.Optional.ofNullable(handlers.get(type));
    }
}
