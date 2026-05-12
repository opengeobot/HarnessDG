/**
 * 功能：SeaTunnel 集成配置
 * 时间：2026-05-12
 * 作者：AxeXie
 */
package com.harnessdg.seatunnel.client;

import lombok.Data;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * SeaTunnel 配置属性
 */
@Data
@Component
@ConfigurationProperties(prefix = "harnessdg.seatunnel")
@ConditionalOnProperty(name = "harnessdg.seatunnel.enabled", havingValue = "true", matchIfMissing = false)
public class SeaTunnelConfig {

    /**
     * SeaTunnel Web 端点
     */
    private String endpoint = "http://localhost:8080";

    /**
     * 超时时间（毫秒）
     */
    private Long timeoutMs = 30000L;

    /**
     * 是否启用
     */
    private Boolean enabled = false;
}
