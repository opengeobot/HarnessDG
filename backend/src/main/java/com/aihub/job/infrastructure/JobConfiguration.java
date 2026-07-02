/*
 * 功能: job 模块装配——退避策略、可靠任务指标（pending/running/dead 计数）与受管调度器驱动的 Worker。
 * 时间: 2026-06-30
 * 作者: AxeXie
 */
package com.aihub.job.infrastructure;

import com.aihub.job.application.BackoffCalculator;
import com.aihub.job.domain.JobRepository;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import java.time.Duration;
import java.util.concurrent.ScheduledFuture;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.ApplicationListener;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;

/**
 * job 模块装配。
 *
 * <p>注册退避计算器、可靠任务指标（按状态暴露队列计数），并以受管 {@code schedulerExecutor}
 * 驱动 Worker 定时领取。Worker 调度受 {@code aihub.job.worker.enabled} 控制（默认开启，测试 Profile 关闭），
 * 不新建裸线程池。
 */
@Configuration
public class JobConfiguration {

    /**
     * @return 指数退避计算器（base 2s，上限 5min）
     */
    @Bean
    public BackoffCalculator backoffCalculator() {
        return new BackoffCalculator(2000L, 300_000L);
    }

    /**
     * 注册可靠任务队列指标（pending/running/retry_wait/dead/succeeded 计数）。
     */
    @Bean
    public JobMetrics jobMetrics(JobRepository jobRepository, ObjectProvider<MeterRegistry> meterRegistry) {
        MeterRegistry registry = meterRegistry.getIfAvailable();
        if (registry != null) {
            registerGauge(registry, jobRepository, "pending", "PENDING");
            registerGauge(registry, jobRepository, "running", "RUNNING");
            registerGauge(registry, jobRepository, "retry_wait", "RETRY_WAIT");
            registerGauge(registry, jobRepository, "dead", "DEAD");
            registerGauge(registry, jobRepository, "succeeded", "SUCCEEDED");
        }
        return new JobMetrics();
    }

    private void registerGauge(MeterRegistry registry, JobRepository repo, String tag, String status) {
        Gauge.builder("job.queue.size", () -> repo.countByStatus(status))
                .tag("status", tag)
                .description("Reliable job count by status")
                .register(registry);
    }

    /**
     * Worker 调度器：以受管 schedulerExecutor 定时驱动 {@link JobWorker#tick()}。
     */
    @Bean
    @ConditionalOnProperty(name = "aihub.job.worker.enabled", havingValue = "true", matchIfMissing = true)
    public JobWorkerScheduler jobWorkerScheduler(JobWorker jobWorker,
                                                  ThreadPoolTaskScheduler schedulerExecutor) {
        return new JobWorkerScheduler(jobWorker, schedulerExecutor);
    }

    /**
     * Worker 调度器实现。应用就绪后以固定速率调度 tick；销毁时取消调度。
     */
    public static class JobWorkerScheduler implements ApplicationListener<ApplicationReadyEvent> {

        private final JobWorker jobWorker;
        private final ThreadPoolTaskScheduler scheduler;
        private ScheduledFuture<?> scheduledFuture;

        JobWorkerScheduler(JobWorker jobWorker, ThreadPoolTaskScheduler scheduler) {
            this.jobWorker = jobWorker;
            this.scheduler = scheduler;
        }

        @Override
        public void onApplicationEvent(ApplicationReadyEvent event) {
            scheduledFuture = scheduler.scheduleAtFixedRate(jobWorker::tick, Duration.ofSeconds(5));
        }

        public void stop() {
            if (scheduledFuture != null) {
                scheduledFuture.cancel(false);
            }
        }
    }

    /** 指标占位 Bean（实际指标在创建时注册）。 */
    public static class JobMetrics {
    }
}
