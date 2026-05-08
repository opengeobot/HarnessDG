package com.harnessdg;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestPropertySource;

/**
 * 功能：Spring Boot 应用上下文集成测试
 * 时间：2026-05-08
 * 作者：AxeXie
 */
@SpringBootTest
@TestPropertySource(locations = "classpath:application-test.yml")
class HarnessDgApplicationTests {

    @Test
    void contextLoads() {
        // 验证 Spring 应用上下文能正常启动
    }
}
