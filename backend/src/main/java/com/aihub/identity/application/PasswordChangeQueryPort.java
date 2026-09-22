/*
 * 功能: 密码改密状态查询端口——供 Web 拦截器读取 must_change_password，避免 api 层依赖仓储。
 * 时间: 2026-07-11
 * 作者: AxeXie
 */
package com.aihub.identity.application;

/**
 * 密码改密状态查询端口。
 */
public interface PasswordChangeQueryPort {

    /**
     * 当前主体是否必须改密。
     *
     * @param principalId 主体 ID
     * @return 未知主体或未设置时返回 {@code false}
     */
    boolean isPasswordChangeRequired(String principalId);
}
