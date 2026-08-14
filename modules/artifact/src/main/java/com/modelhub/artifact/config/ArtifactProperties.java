package com.modelhub.artifact.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 对象存储配置（05 §2/§6.1）：Java/Worker 走 internal endpoint，预签名 URL 走
 * public base URL；public base URL 必须对真实浏览器/CLI/部署网段可达。
 */
@ConfigurationProperties(prefix = "modelhub.artifact")
public class ArtifactProperties {

    /** 服务端内部端点（Java/Worker 使用，05 §2）。 */
    private String internalEndpoint = "http://localhost:9000";
    /** 预签名 URL 公网基址（浏览器/CLI 可达，禁止 minio:9000/localhost 服务名）。 */
    private String publicBaseUrl = "http://localhost:9000";
    private String accessKey = "modelhub";
    private String secretKey = "";
    private String region = "us-east-1";
    /** 已发布原始二进制 bucket（05 §4，不按资源类型分 bucket）。 */
    private String bucket = "artifacts";
    /** 上传会话有效期（05 §6.1 默认 72 小时）。 */
    private int sessionTtlHours = 72;
    /** part URL 有效期（05 §6.1 默认 15 分钟且可刷新）。 */
    private int partUrlTtlMinutes = 15;
    /** 下载 URL 有效期（07 章安全策略；private/gated 应更短）。 */
    private int downloadUrlTtlSeconds = 600;
    /** private/gated repo download URL TTL (shorter, <= 600). */
    private int downloadUrlTtlSecondsPrivate = 300;
    /** git source 阈值：小于该字节数且为文本类的文件走 Gitea Git 对象（05 §3 部署配置）。 */
    private long gitSourceMaxBytes = 10L * 1024 * 1024;
    /** 业务 API 公网基址：git source 自建下载端点的绝对 URL 前缀。 */
    private String apiBaseUrl = "http://localhost:8080";
    /** 默认客户端并发（05 §6.1）。 */
    private int defaultMaxConcurrency = 3;

    public String getInternalEndpoint() { return internalEndpoint; }
    public void setInternalEndpoint(String internalEndpoint) { this.internalEndpoint = internalEndpoint; }
    public String getPublicBaseUrl() { return publicBaseUrl; }
    public void setPublicBaseUrl(String publicBaseUrl) { this.publicBaseUrl = publicBaseUrl; }
    public String getAccessKey() { return accessKey; }
    public void setAccessKey(String accessKey) { this.accessKey = accessKey; }
    public String getSecretKey() { return secretKey; }
    public void setSecretKey(String secretKey) { this.secretKey = secretKey; }
    public String getRegion() { return region; }
    public void setRegion(String region) { this.region = region; }
    public String getBucket() { return bucket; }
    public void setBucket(String bucket) { this.bucket = bucket; }
    public int getSessionTtlHours() { return sessionTtlHours; }
    public void setSessionTtlHours(int sessionTtlHours) { this.sessionTtlHours = sessionTtlHours; }
    public int getPartUrlTtlMinutes() { return partUrlTtlMinutes; }
    public void setPartUrlTtlMinutes(int partUrlTtlMinutes) { this.partUrlTtlMinutes = partUrlTtlMinutes; }
    public int getDownloadUrlTtlSeconds() { return downloadUrlTtlSeconds; }
    public void setDownloadUrlTtlSeconds(int downloadUrlTtlSeconds) { this.downloadUrlTtlSeconds = downloadUrlTtlSeconds; }
    public int getDownloadUrlTtlSecondsPrivate() { return downloadUrlTtlSecondsPrivate; }
    public void setDownloadUrlTtlSecondsPrivate(int downloadUrlTtlSecondsPrivate) { this.downloadUrlTtlSecondsPrivate = downloadUrlTtlSecondsPrivate; }
    public long getGitSourceMaxBytes() { return gitSourceMaxBytes; }
    public void setGitSourceMaxBytes(long gitSourceMaxBytes) { this.gitSourceMaxBytes = gitSourceMaxBytes; }
    public String getApiBaseUrl() { return apiBaseUrl; }
    public void setApiBaseUrl(String apiBaseUrl) { this.apiBaseUrl = apiBaseUrl; }
    public int getDefaultMaxConcurrency() { return defaultMaxConcurrency; }
    public void setDefaultMaxConcurrency(int defaultMaxConcurrency) { this.defaultMaxConcurrency = defaultMaxConcurrency; }
}
