/*
 * 功能: 平台自定义指标单元测试——验证审计事件与 Webhook 投递计数器递增。
 * 时间: 2026-07-01
 * 作者: AxeXie
 */
package com.aihub.platform.observability.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;

/**
 * {@link PlatformMetrics} 单元测试。
 */
class PlatformMetricsTest {

    @SuppressWarnings("unchecked")
    private ObjectProvider<MeterRegistry> registryProvider(MeterRegistry registry) {
        ObjectProvider<MeterRegistry> provider = mock(ObjectProvider.class);
        when(provider.getIfAvailable()).thenReturn(registry);
        return provider;
    }

    @Test
    void shouldIncrementAuditEventCounter() {
        SimpleMeterRegistry registry = new SimpleMeterRegistry();
        PlatformMetrics metrics = new PlatformMetrics(registryProvider(registry));

        metrics.recordAuditEvent("ROLE_CREATED", "SUCCEEDED");
        metrics.recordAuditEvent("ROLE_CREATED", "SUCCEEDED");
        metrics.recordAuditEvent("ROLE_CREATED", "DENIED");

        assertThat(metrics.auditEventCount("ROLE_CREATED", "SUCCEEDED")).isEqualTo(2);
        assertThat(metrics.auditEventCount("ROLE_CREATED", "DENIED")).isEqualTo(1);
    }

    @Test
    void shouldIncrementWebhookDeliveryCounter() {
        SimpleMeterRegistry registry = new SimpleMeterRegistry();
        PlatformMetrics metrics = new PlatformMetrics(registryProvider(registry));

        metrics.recordWebhookDelivery("DELIVERED");
        metrics.recordWebhookDelivery("DELIVERED");
        metrics.recordWebhookDelivery("FAILED");
        metrics.recordWebhookDelivery("DEAD");

        assertThat(metrics.webhookDeliveryCount("DELIVERED")).isEqualTo(2);
        assertThat(metrics.webhookDeliveryCount("FAILED")).isEqualTo(1);
        assertThat(metrics.webhookDeliveryCount("DEAD")).isEqualTo(1);
    }

    @Test
    void shouldNoopWhenNoMeterRegistry() {
        PlatformMetrics metrics = new PlatformMetrics(registryProvider(null));

        metrics.recordAuditEvent("USER_LOGIN", "SUCCEEDED");
        metrics.recordWebhookDelivery("DELIVERED");

        assertThat(metrics.auditEventCount("USER_LOGIN", "SUCCEEDED")).isZero();
        assertThat(metrics.webhookDeliveryCount("DELIVERED")).isZero();
    }
}
