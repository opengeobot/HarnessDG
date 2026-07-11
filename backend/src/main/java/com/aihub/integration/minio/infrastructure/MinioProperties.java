/*
 * 功能: MinIO 连接与存储配额配置属性。
 * 时间: 2026-07-11
 * 作者: AxeXie
 */
package com.aihub.integration.minio.infrastructure;

import com.aihub.version.domain.DvcStorageProperties;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * MinIO 连接配置属性。
 */
@ConfigurationProperties(prefix = "aihub.minio")
public class MinioProperties implements DvcStorageProperties {

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

    /** 平台存储配额（字节）；0 表示不启用配额告警。 */
    private long quotaBytes = 0L;

    /** 配额告警阈值（使用率百分比，0–100）。 */
    private int quotaWarningPercent = 80;

    public String getEndpoint() { return endpoint; }
    public void setEndpoint(String endpoint) { this.endpoint = endpoint; }
    public String getAccessKey() { return accessKey; }
    public void setAccessKey(String accessKey) { this.accessKey = accessKey; }
    public String getSecretKey() { return secretKey; }
    public void setSecretKey(String secretKey) { this.secretKey = secretKey; }
    public boolean isSecure() { return secure; }
    public void setSecure(boolean secure) { this.secure = secure; }
    @Override
    public String getExternalEndpoint() { return externalEndpoint; }
    public void setExternalEndpoint(String externalEndpoint) { this.externalEndpoint = externalEndpoint; }
    public String getDvcAccessKey() { return dvcAccessKey; }
    public void setDvcAccessKey(String dvcAccessKey) { this.dvcAccessKey = dvcAccessKey; }
    public String getDvcSecretKey() { return dvcSecretKey; }
    public void setDvcSecretKey(String dvcSecretKey) { this.dvcSecretKey = dvcSecretKey; }

    /** 解析 DVC 专用访问密钥（不回退 root 密钥）。 */
    @Override
    public String resolveDvcAccessKey() {
        return dvcAccessKey != null && !dvcAccessKey.isBlank() ? dvcAccessKey : null;
    }

    /** 解析 DVC 专用秘密密钥（不回退 root 密钥）。 */
    @Override
    public String resolveDvcSecretKey() {
        return dvcSecretKey != null && !dvcSecretKey.isBlank() ? dvcSecretKey : null;
    }

    public long getQuotaBytes() { return quotaBytes; }
    public void setQuotaBytes(long quotaBytes) { this.quotaBytes = quotaBytes; }
    public int getQuotaWarningPercent() { return quotaWarningPercent; }
    public void setQuotaWarningPercent(int quotaWarningPercent) { this.quotaWarningPercent = quotaWarningPercent; }
}
