package com.modelhub.app;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.boot.autoconfigure.security.servlet.UserDetailsServiceAutoConfiguration;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * ModelHub 模块化单体启动类（ADR-001）：
 * 扫描 com.modelhub 全部模块，JPA 实体与仓库跨模块装配。
 * 排除 UserDetailsServiceAutoConfiguration：认证完全走自有 JWT，禁止内存默认账号。
 */
@SpringBootApplication(scanBasePackages = "com.modelhub",
        exclude = UserDetailsServiceAutoConfiguration.class)
@EntityScan(basePackages = "com.modelhub")
@EnableJpaRepositories(basePackages = "com.modelhub")
@EnableScheduling
public class ModelHubApplication {

    public static void main(String[] args) {
        SpringApplication.run(ModelHubApplication.class, args);
    }
}
