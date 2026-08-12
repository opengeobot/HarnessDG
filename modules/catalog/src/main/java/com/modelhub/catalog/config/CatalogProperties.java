package com.modelhub.catalog.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * catalog 域运行参数：Outbox 轮询与 provisioning 重试策略（05 §8/§10.1）。
 */
@ConfigurationProperties(prefix = "modelhub.catalog")
public class CatalogProperties {

    /** Outbox 轮询间隔（毫秒），测试环境可调小加速收敛。 */
    private long pollIntervalMs = 2000;

    /** provisioning 自动重试次数上限，超过进入 failed 等待管理员处理（05 §8）。 */
    private int provisionMaxRetries = 3;

    /** 删除保留天数（05 §9.2 默认 30 天）。 */
    private int retentionDays = 30;

    public long getPollIntervalMs() { return pollIntervalMs; }
    public void setPollIntervalMs(long pollIntervalMs) { this.pollIntervalMs = pollIntervalMs; }
    public int getProvisionMaxRetries() { return provisionMaxRetries; }
    public void setProvisionMaxRetries(int provisionMaxRetries) { this.provisionMaxRetries = provisionMaxRetries; }
    public int getRetentionDays() { return retentionDays; }
    public void setRetentionDays(int retentionDays) { this.retentionDays = retentionDays; }
}
