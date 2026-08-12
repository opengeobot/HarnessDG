package com.modelhub.identity.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * bootstrap 配置（02 §2）：只负责一次性创建首条持久平台角色记录；
 * 运行期不得把环境变量或用户名当作授权事实。
 */
@ConfigurationProperties(prefix = "modelhub.bootstrap")
public record BootstrapProperties(boolean enabled, String username, String password) {

    public BootstrapProperties {
        if (username == null) username = "";
        if (password == null) password = "";
    }
}
