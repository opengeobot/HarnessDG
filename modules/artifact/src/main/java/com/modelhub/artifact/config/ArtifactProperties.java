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
    /** SEC-02（07 §2）：private/gated 下载 URL 最长 10 分钟，超限配置一律钳制到 600s。 */
    private static final int MAX_PRIVATE_DOWNLOAD_TTL_SECONDS = 600;
    /** git source 阈值：小于该字节数且为文本类的文件走 Gitea Git 对象（05 §3 部署配置）。 */
    private long gitSourceMaxBytes = 10L * 1024 * 1024;
    /** 业务 API 公网基址：git source 自建下载端点的绝对 URL 前缀。 */
    private String apiBaseUrl = "http://localhost:8080";
    /** 默认客户端并发（05 §6.1）。 */
    private int defaultMaxConcurrency = 3;
    /** 预览 Worker 轮询间隔（05 §5；测试可调小加速收敛）。 */
    private long previewIntervalMs = 1000;
    /** 配额（05 §11）：单文件/单仓库总量/并发上传会话，配置驱动（modelhub.artifact.quota.*）。 */
    private Quota quota = new Quota();

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
    /** SEC-02：写入即钳制到 [1, 600]，读取方无需再次校验（07 §2 最长 10 分钟）。 */
    public void setDownloadUrlTtlSecondsPrivate(int downloadUrlTtlSecondsPrivate) {
        this.downloadUrlTtlSecondsPrivate = Math.max(1,
                Math.min(downloadUrlTtlSecondsPrivate, MAX_PRIVATE_DOWNLOAD_TTL_SECONDS));
    }
    public long getGitSourceMaxBytes() { return gitSourceMaxBytes; }
    public void setGitSourceMaxBytes(long gitSourceMaxBytes) { this.gitSourceMaxBytes = gitSourceMaxBytes; }
    public String getApiBaseUrl() { return apiBaseUrl; }
    public void setApiBaseUrl(String apiBaseUrl) { this.apiBaseUrl = apiBaseUrl; }
    public int getDefaultMaxConcurrency() { return defaultMaxConcurrency; }
    public void setDefaultMaxConcurrency(int defaultMaxConcurrency) { this.defaultMaxConcurrency = defaultMaxConcurrency; }
    public long getPreviewIntervalMs() { return previewIntervalMs; }
    public void setPreviewIntervalMs(long previewIntervalMs) { this.previewIntervalMs = previewIntervalMs; }
    public Quota getQuota() { return quota; }
    public void setQuota(Quota quota) { this.quota = quota; }

    /** 上传配额（05 §11）：单文件、单仓库总量与并发上传会话上限。 */
    public static class Quota {

        /** 单文件大小上限（FILE-003：默认 50GiB = 53687091200 bytes）。 */
        private long maxFileSizeBytes = 53687091200L;
        /** 单仓库已发布（staging/active）文件总量上限；0 = 不限制。 */
        private long maxRepoTotalBytes = 0;
        /** 单仓库非终态上传会话并发上限（05 §11 并发上传数）。 */
        private int maxConcurrentUploads = 5;

        public long getMaxFileSizeBytes() { return maxFileSizeBytes; }
        public void setMaxFileSizeBytes(long maxFileSizeBytes) { this.maxFileSizeBytes = maxFileSizeBytes; }
        public long getMaxRepoTotalBytes() { return maxRepoTotalBytes; }
        public void setMaxRepoTotalBytes(long maxRepoTotalBytes) { this.maxRepoTotalBytes = maxRepoTotalBytes; }
        public int getMaxConcurrentUploads() { return maxConcurrentUploads; }
        public void setMaxConcurrentUploads(int maxConcurrentUploads) { this.maxConcurrentUploads = maxConcurrentUploads; }
    }
}
