/*
 * 功能: Spring Boot 应用启动入口，扫描 com.aihub 下所有模块。
 * 时间: 2026-06-29
 * 作者: AxeXie
 */
package com.aihub.bootstrap;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * 平台后端启动类（模块化单体）。
 *
 * <p>组件扫描根设为 {@code com.aihub}，使各模块在同一上下文内装配。
 *
 * <p>Mapper 扫描必须覆盖**任意深度**的模块 {@code infrastructure} 子包：既有一级模块包
 * （{@code com.aihub.asset.infrastructure}），也有两级子域包
 * （{@code com.aihub.asset.discussion.infrastructure}）。使用单星 {@code *} 只会匹配一级，
 * 会使更深层模块的 Mapper 静默逃逸注册，导致仓储 Bean 装配失败、可部署 Profile 无法启动。
 * 该覆盖范围由 {@code MapperScanCoverageTest} 门禁守护。
 */
@SpringBootApplication(scanBasePackages = "com.aihub")
@MapperScan("com.aihub.**.infrastructure")
@EnableScheduling
public class AihubApplication {

    public static void main(String[] args) {
        SpringApplication.run(AihubApplication.class, args);
    }
}
