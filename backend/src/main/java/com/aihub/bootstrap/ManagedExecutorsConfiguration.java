/*
 * 功能: 受管 Executor 装配——注册四类受管线程池/调度器并绑定 Micrometer 指标。
 * 时间: 2026-06-30
 * 作者: AxeXie
 */
package com.aihub.bootstrap;

import com.aihub.platform.concurrency.ContextPropagatingTaskDecorator;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Tag;
import io.micrometer.core.instrument.Tags;
import io.micrometer.core.instrument.binder.jvm.ExecutorServiceMetrics;
import java.util.concurrent.ThreadPoolExecutor;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.task.TaskDecorator;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;

/**
 * 受管 Executor 装配。
 *
 * <p>注册 PRD 5.11 要求的四类进程内短任务线程池：{@code ioExecutor}、{@code notificationExecutor}、
 * {@code auditExecutor} 与调度器 {@code schedulerExecutor}。统一：
 * <ul>
 *   <li>使用 {@link ContextPropagatingTaskDecorator} 传播 PrincipalContext 与 MDC；</li>
 *   <li>拒绝策略采用 {@code CallerRunsPolicy}，绝不静默丢弃任务；</li>
 *   <li>通过 Micrometer {@link ExecutorServiceMetrics} 暴露活跃数、队列深度、完成数、拒绝数与耗时。</li>
 * </ul>
 *
 * <p>这些 Executor 仅用于进程内短任务；可靠任务（DVC/发布/Webhook/对账）必须走持久化任务系统。
 */
@Configuration
public class ManagedExecutorsConfiguration {

    private final ObjectProvider<MeterRegistry> meterRegistry;

    public ManagedExecutorsConfiguration(ObjectProvider<MeterRegistry> meterRegistry) {
        this.meterRegistry = meterRegistry;
    }

    /**
     * @return 上下文传播任务装饰器
     */
    @Bean
    public TaskDecorator contextPropagatingTaskDecorator() {
        return new ContextPropagatingTaskDecorator();
    }

    /**
     * @return IO 密集型短任务线程池（如轻量外呼、缓存刷新）
     */
    @Bean(name = "ioExecutor", destroyMethod = "shutdown")
    public ThreadPoolTaskExecutor ioExecutor(TaskDecorator contextPropagatingTaskDecorator) {
        return buildExecutor("io", 4, 16, 200, contextPropagatingTaskDecorator);
    }

    /**
     * @return 非关键通知发送线程池
     */
    @Bean(name = "notificationExecutor", destroyMethod = "shutdown")
    public ThreadPoolTaskExecutor notificationExecutor(TaskDecorator contextPropagatingTaskDecorator) {
        return buildExecutor("notification", 2, 8, 500, contextPropagatingTaskDecorator);
    }

    /**
     * @return 审计批量发送线程池
     */
    @Bean(name = "auditExecutor", destroyMethod = "shutdown")
    public ThreadPoolTaskExecutor auditExecutor(TaskDecorator contextPropagatingTaskDecorator) {
        return buildExecutor("audit", 2, 4, 1000, contextPropagatingTaskDecorator);
    }

    /**
     * @return 轻量周期调度器
     */
    @Bean(name = "schedulerExecutor", destroyMethod = "shutdown")
    public ThreadPoolTaskScheduler schedulerExecutor(TaskDecorator contextPropagatingTaskDecorator) {
        ThreadPoolTaskScheduler scheduler = new ThreadPoolTaskScheduler();
        scheduler.setPoolSize(2);
        scheduler.setThreadNamePrefix("scheduler-");
        scheduler.setTaskDecorator(contextPropagatingTaskDecorator);
        // 拒绝策略：由提交线程执行，绝不静默丢弃。
        scheduler.setRejectedExecutionHandler(new ThreadPoolExecutor.CallerRunsPolicy());
        scheduler.setWaitForTasksToCompleteOnShutdown(true);
        scheduler.setAwaitTerminationSeconds(20);
        scheduler.initialize();
        bindMetrics(scheduler.getScheduledThreadPoolExecutor(), "scheduler");
        return scheduler;
    }

    private ThreadPoolTaskExecutor buildExecutor(String name, int coreSize, int maxSize,
                                                 int queueCapacity, TaskDecorator decorator) {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(coreSize);
        executor.setMaxPoolSize(maxSize);
        executor.setQueueCapacity(queueCapacity);
        executor.setThreadNamePrefix(name + "-");
        executor.setTaskDecorator(decorator);
        // 队列满时由提交线程执行，绝不静默丢弃任务。
        executor.setRejectedExecutionHandler(new ThreadPoolExecutor.CallerRunsPolicy());
        executor.setWaitForTasksToCompleteOnShutdown(true);
        executor.setAwaitTerminationSeconds(20);
        executor.initialize();
        bindMetrics(executor.getThreadPoolExecutor(), name);
        return executor;
    }

    private void bindMetrics(java.util.concurrent.ExecutorService executorService, String name) {
        MeterRegistry registry = meterRegistry.getIfAvailable();
        if (registry == null) {
            return;
        }
        ExecutorServiceMetrics.monitor(registry, executorService, name,
                Tags.of(Tag.of("executor", name)));
    }
}
