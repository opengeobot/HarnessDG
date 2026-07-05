package com.aihub.integration.minio.infrastructure;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * MinIO 连接配置属性。
 */
@ConfigurationProperties(prefix = "aihub.minio")
public class MinioProperties {

    /** MinIO 端点 URL（如 http://localhost:9000）。 */
    private String endpoint = "http://localhost:9000";

    /** 访问密钥。 */
    private String accessKey = "minioadmin";

    /** 秘密密钥。 */
    private String secretKey = "minioadmin";

    /** 是否使用 HTTPS。 */
    private boolean secure = false;

    /** 预签名 URL 的外部端点（可选，用于 Docker 网络映射场景）。 */
    private String externalEndpoint;

    public String getEndpoint() { return endpoint; }
    public void setEndpoint(String endpoint) { this.endpoint = endpoint; }
    public String getAccessKey() { return accessKey; }
    public void setAccessKey(String accessKey) { this.accessKey = accessKey; }
    public String getSecretKey() { return secretKey; }
    public void setSecretKey(String secretKey) { this.secretKey = secretKey; }
    public boolean isSecure() { return secure; }
    public void setSecure(boolean secure) { this.secure = secure; }
    public String getExternalEndpoint() { return externalEndpoint; }
    public void setExternalEndpoint(String externalEndpoint) { this.externalEndpoint = externalEndpoint; }
}
