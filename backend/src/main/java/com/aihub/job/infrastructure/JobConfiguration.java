/*
 * 功能: job 模块装配——退避策略、可靠任务指标（pending/running/dead 计数）与受管调度器驱动的 Worker。
 * 时间: 2026-06-30
 * 作者: AxeXie
 */
package com.aihub.job.infrastructure;

import com.aihub.job.application.BackoffCalculator;
import com.aihub.job.application.JobAuditPort;
import com.aihub.job.application.JobNotificationPort;
import com.aihub.job.domain.JobAttemptRepository;
import com.aihub.job.domain.JobRepository;
import com.aihub.platform.observability.application.PlatformMetrics;
import com.aihub.shared.logging.SensitiveDataMasker;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import java.time.Clock;
import java.time.Duration;
import java.util.concurrent.ScheduledFuture;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.ApplicationListener;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;
import org.springframework.transaction.support.TransactionTemplate;

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
     * @return 指数退避计算器（base/cap 由受控配置提供，默认 2s / 5min）
     */
    @Bean
    public BackoffCalculator backoffCalculator(
            @Value("${aihub.job.backoff.base-millis:2000}") long baseMillis,
            @Value("${aihub.job.backoff.cap-millis:300000}") long capMillis) {
        return new BackoffCalculator(baseMillis, capMillis);
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
     * Worker：领取、执行、退避重试与租约恢复。
     *
     * <p>显式装配而非组件扫描：需要注入租约时长与脱敏器等受控配置。
     */
    @Bean
    public JobWorker jobWorker(JobRepository jobRepository,
                               JobAttemptRepository jobAttemptRepository,
                               JobHandlerRegistry handlerRegistry,
                               BackoffCalculator backoff,
                               ObjectProvider<JobNotificationPort> notificationPortProvider,
                               ObjectProvider<JobAuditPort> auditPortProvider,
                               TransactionTemplate transactionTemplate,
                               Clock clock,
                               ObjectProvider<MeterRegistry> meterRegistryProvider,
                               SensitiveDataMasker sensitiveDataMasker,
                               ObjectProvider<PlatformMetrics> platformMetricsProvider,
                               @Value("${aihub.job.worker.lease-seconds:60}") long leaseSeconds) {
        return new JobWorker(jobRepository, jobAttemptRepository, handlerRegistry, backoff,
                notificationPortProvider, auditPortProvider, transactionTemplate,
                clock, meterRegistryProvider, sensitiveDataMasker, leaseSeconds,
                platformMetricsProvider);
    }

    /**
     * Worker 调度器：以受管 schedulerExecutor 定时驱动 {@link JobWorker#tick()}。
     */
    @Bean
    @ConditionalOnProperty(name = "aihub.job.worker.enabled", havingValue = "true", matchIfMissing = true)
    public JobWorkerScheduler jobWorkerScheduler(
            JobWorker jobWorker,
            ThreadPoolTaskScheduler schedulerExecutor,
            @Value("${aihub.job.worker.interval-seconds:5}") long intervalSeconds) {
        return new JobWorkerScheduler(jobWorker, schedulerExecutor, Duration.ofSeconds(intervalSeconds));
    }

    /**
     * Worker 调度器实现。应用就绪后以固定速率调度 tick；销毁时取消调度。
     *
     * <p>{@link JobWorker#tick()} 自身已捕获全部异常，异常不会逸出到调度器；此处不再依赖
     * {@code ScheduledThreadPoolExecutor} 的默认行为，避免周期任务被静默取消后永久停摆。
     */
    public static class JobWorkerScheduler implements ApplicationListener<ApplicationReadyEvent> {

        private final JobWorker jobWorker;
        private final ThreadPoolTaskScheduler scheduler;
        private final Duration interval;
        private ScheduledFuture<?> scheduledFuture;

        JobWorkerScheduler(JobWorker jobWorker, ThreadPoolTaskScheduler scheduler, Duration interval) {
            this.jobWorker = jobWorker;
            this.scheduler = scheduler;
            this.interval = interval;
        }

        @Override
        public void onApplicationEvent(ApplicationReadyEvent event) {
            scheduledFuture = scheduler.scheduleAtFixedRate(jobWorker::tick, interval);
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
