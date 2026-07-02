/*
 * 功能: 异步任务上下文传播装饰器，将提交线程的 PrincipalContext 与 MDC 复制到执行线程。
 * 时间: 2026-06-30
 * 作者: AxeXie
 */
package com.aihub.platform.concurrency;

import com.aihub.shared.identity.PrincipalContext;
import com.aihub.shared.identity.PrincipalContextHolder;
import java.util.Map;
import org.slf4j.MDC;
import org.springframework.core.task.TaskDecorator;

/**
 * 上下文传播装饰器。
 *
 * <p>受管 Executor 执行异步任务时，提交线程的 {@link PrincipalContext} 与 MDC 不会自动随线程切换。
 * 本装饰器在任务执行前后复制并清理这两类上下文，保证 {@code traceId}/{@code principalId} 链路连续。
 */
public class ContextPropagatingTaskDecorator implements TaskDecorator {

    @Override
    public Runnable decorate(Runnable runnable) {
        PrincipalContext context = PrincipalContextHolder.current().orElse(null);
        Map<String, String> mdc = MDC.getCopyOfContextMap();
        return () -> {
            if (context != null) {
                PrincipalContextHolder.set(context);
            }
            if (mdc != null) {
                MDC.setContextMap(mdc);
            }
            try {
                runnable.run();
            } finally {
                PrincipalContextHolder.clear();
                MDC.clear();
            }
        };
    }
}
