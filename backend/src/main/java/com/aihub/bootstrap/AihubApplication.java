/*
 * 功能: Spring Boot 应用启动入口，扫描 com.aihub 下所有模块。
 * 时间: 2026-06-29
 * 作者: AxeXie
 */
package com.aihub.bootstrap;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * 平台后端启动类（模块化单体）。
 *
 * <p>组件扫描根设为 {@code com.aihub}，使各模块在同一上下文内装配；
 * Mapper 扫描限定到各模块 {@code infrastructure} 子包，骨架阶段暂无 Mapper。
 */
@SpringBootApplication(scanBasePackages = "com.aihub")
@MapperScan("com.aihub.*.infrastructure")
public class AihubApplication {

    public static void main(String[] args) {
        SpringApplication.run(AihubApplication.class, args);
    }
}
