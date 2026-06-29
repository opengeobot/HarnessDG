/*
 * 功能: 公共能力 Bean 装配，向上下文注册 shared-kernel 的无状态组件。
 * 时间: 2026-06-29
 * 作者: AxeXie
 */
package com.aihub.bootstrap;

import com.aihub.shared.id.IdGenerator;
import com.aihub.shared.id.UlidIdGenerator;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 公共能力 Bean 装配。
 *
 * <p>shared-kernel 自身不依赖 Spring，由 bootstrap 负责把其无状态实现注册为 Bean。
 */
@Configuration
public class SharedKernelConfiguration {

    /**
     * @return 基于 ULID 的业务 ID 生成器
     */
    @Bean
    public IdGenerator idGenerator() {
        return new UlidIdGenerator();
    }
}
