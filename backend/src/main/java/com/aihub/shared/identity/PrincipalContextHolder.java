/*
 * 功能: 基于 ThreadLocal 的请求主体上下文持有者，提供建立、读取与清理能力。
 * 时间: 2026-06-29
 * 作者: AxeXie
 */
package com.aihub.shared.identity;

import java.util.Optional;

/**
 * 请求主体上下文持有者。
 *
 * <p>业务代码只通过本类获取当前主体；入口适配器负责 {@link #set} 并在请求结束时 {@link #clear}，
 * 避免线程复用导致的上下文串台。
 */
public final class PrincipalContextHolder {

    private static final ThreadLocal<PrincipalContext> CONTEXT = new ThreadLocal<>();

    private PrincipalContextHolder() {
    }

    /**
     * 绑定当前线程的主体上下文。
     *
     * @param context 主体上下文，不可为 {@code null}
     */
    public static void set(PrincipalContext context) {
        if (context == null) {
            throw new IllegalArgumentException("PrincipalContext must not be null");
        }
        CONTEXT.set(context);
    }

    /**
     * @return 当前线程主体上下文（可能为空）
     */
    public static Optional<PrincipalContext> current() {
        return Optional.ofNullable(CONTEXT.get());
    }

    /**
     * @return 当前线程主体上下文，缺失时抛出异常
     */
    public static PrincipalContext require() {
        PrincipalContext context = CONTEXT.get();
        if (context == null) {
            throw new IllegalStateException("No PrincipalContext bound to current thread");
        }
        return context;
    }

    /**
     * 清理当前线程主体上下文，必须在请求结束时调用。
     */
    public static void clear() {
        CONTEXT.remove();
    }
}
