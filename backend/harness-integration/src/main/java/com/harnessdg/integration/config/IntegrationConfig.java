package com.harnessdg.integration.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * 功能：第三方集成配置属性
 * 时间：2026-05-08
 * 作者：AxeXie
 */
@Data
@Component
@ConfigurationProperties(prefix = "harnessdg.integration")
public class IntegrationConfig {

    private DagsterConfig dagster = new DagsterConfig();
    private SeaTunnelConfig seaTunnel = new SeaTunnelConfig();
    private OpenMetadataConfig openMetadata = new OpenMetadataConfig();

    @Data
    public static class DagsterConfig {
        private String endpoint = "http://localhost:3000";
        private String graphqlPath = "/graphql";
        private int timeoutMs = 30000;
    }

    @Data
    public static class SeaTunnelConfig {
        private String endpoint = "http://localhost:8080";
        private String apiPath = "/seatunnel/api/v1";
        private int timeoutMs = 60000;
    }

    @Data
    public static class OpenMetadataConfig {
        private String endpoint = "http://localhost:8585";
        private String apiPath = "/api/v1";
        private String apiKey = "";
        private int timeoutMs = 30000;
    }
}
