package com.modelhub.catalog.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Gitea Git Provider 配置（05 §2/§8）：客户端可达网络 + basic auth 管理员凭据。
 */
@ConfigurationProperties(prefix = "modelhub.gitea")
public class GiteaProperties {

    private String baseUrl = "http://localhost:3000";
    private String username = "modelhub";
    private String password = "";
    private int connectTimeoutMs = 3000;
    private int readTimeoutMs = 10_000;

    public String getBaseUrl() { return baseUrl; }
    public void setBaseUrl(String baseUrl) { this.baseUrl = baseUrl; }
    public String getUsername() { return username; }
    public void setUsername(String username) { this.username = username; }
    public String getPassword() { return password; }
    public void setPassword(String password) { this.password = password; }
    public int getConnectTimeoutMs() { return connectTimeoutMs; }
    public void setConnectTimeoutMs(int connectTimeoutMs) { this.connectTimeoutMs = connectTimeoutMs; }
    public int getReadTimeoutMs() { return readTimeoutMs; }
    public void setReadTimeoutMs(int readTimeoutMs) { this.readTimeoutMs = readTimeoutMs; }
}
