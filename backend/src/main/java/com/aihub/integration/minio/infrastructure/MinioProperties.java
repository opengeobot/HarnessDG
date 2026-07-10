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

    /** DVC 专用访问密钥（与平台 root 密钥隔离；未配置时回退 dvc-access-key 占位）。 */
    private String dvcAccessKey;

    /** DVC 专用秘密密钥（敏感，禁止记录）。 */
    private String dvcSecretKey;

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
    public String getDvcAccessKey() { return dvcAccessKey; }
    public void setDvcAccessKey(String dvcAccessKey) { this.dvcAccessKey = dvcAccessKey; }
    public String getDvcSecretKey() { return dvcSecretKey; }
    public void setDvcSecretKey(String dvcSecretKey) { this.dvcSecretKey = dvcSecretKey; }

    /** 解析 DVC 专用访问密钥（不回退 root 密钥）。 */
    public String resolveDvcAccessKey() {
        return dvcAccessKey != null && !dvcAccessKey.isBlank() ? dvcAccessKey : null;
    }

    /** 解析 DVC 专用秘密密钥（不回退 root 密钥）。 */
    public String resolveDvcSecretKey() {
        return dvcSecretKey != null && !dvcSecretKey.isBlank() ? dvcSecretKey : null;
    }
}
