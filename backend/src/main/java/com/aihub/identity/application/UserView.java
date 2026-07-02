/*
 * 功能: 本地用户视图，对应契约 UserView；绝不包含口令哈希。
 * 时间: 2026-06-30
 * 作者: AxeXie
 */
package com.aihub.identity.application;

import com.aihub.identity.domain.LocalUser;
import com.aihub.identity.domain.UserStatus;
import java.time.Instant;

/**
 * 本地用户视图。
 *
 * @param userId              用户 ID（usr_）
 * @param principalId         主体 ID（prn_）
 * @param username            登录用户名
 * @param displayName         展示名称
 * @param email               邮箱
 * @param locale              语言偏好
 * @param status              用户状态
 * @param forcePasswordChange 是否必须修改口令
 * @param lastLoginAt         最近登录时间
 * @param createdAt           创建时间
 * @param updatedAt           更新时间
 */
public record UserView(String userId,
                       String principalId,
                       String username,
                       String displayName,
                       String email,
                       String locale,
                       UserStatus status,
                       boolean forcePasswordChange,
                       Instant lastLoginAt,
                       Instant createdAt,
                       Instant updatedAt) {

    /**
     * 由领域聚合构造视图（不暴露口令哈希）。
     */
    public static UserView from(LocalUser user) {
        return new UserView(
                user.userId(),
                user.principalId(),
                user.username(),
                user.displayName(),
                user.email(),
                user.locale(),
                user.status(),
                user.mustChangePassword(),
                user.lastLoginAt(),
                user.createdAt(),
                user.updatedAt());
    }
}
