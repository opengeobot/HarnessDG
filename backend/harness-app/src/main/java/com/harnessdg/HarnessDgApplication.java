/**
 * 功能：HarnessDG Spring Boot 应用入口
 * 时间：2026-05-07
 * 作者：AxeXie
 */
package com.harnessdg;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableAsync;

@SpringBootApplication
@EnableAsync
public class HarnessDgApplication {

    public static void main(String[] args) {
        SpringApplication.run(HarnessDgApplication.class, args);
    }
}
