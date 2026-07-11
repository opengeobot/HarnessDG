/*
 * 功能: 邮件通知渠道配置属性。
 * 时间: 2026-07-11
 * 作者: AxeXie
 */
package com.aihub.notification.infrastructure;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 邮件渠道 SMTP 配置，绑定 {@code aihub.notification.email.*}。
 */
@ConfigurationProperties(prefix = "aihub.notification.email")
public class EmailChannelProperties {

    private boolean enabled;
    private String from = "noreply@aihub.local";
    private String host = "localhost";
    private int port = 587;
    private String username;
    private String password;
    private boolean auth;
    private boolean startTls = true;

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public String getFrom() {
        return from;
    }

    public void setFrom(String from) {
        this.from = from;
    }

    public String getHost() {
        return host;
    }

    public void setHost(String host) {
        this.host = host;
    }

    public int getPort() {
        return port;
    }

    public void setPort(int port) {
        this.port = port;
    }

    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public String getPassword() {
        return password;
    }

    public void setPassword(String password) {
        this.password = password;
    }

    public boolean isAuth() {
        return auth || (username != null && !username.isBlank());
    }

    public void setAuth(boolean auth) {
        this.auth = auth;
    }

    public boolean isStartTls() {
        return startTls;
    }

    public void setStartTls(boolean startTls) {
        this.startTls = startTls;
    }
}
