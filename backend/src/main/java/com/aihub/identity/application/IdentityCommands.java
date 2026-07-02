/*
 * 功能: identity 应用层命令对象集合，承载用例输入参数。
 * 时间: 2026-06-30
 * 作者: AxeXie
 */
package com.aihub.identity.application;

import java.util.List;
import java.util.Set;

/**
 * identity 应用层命令对象集合。
 *
 * <p>口令/凭据等敏感明文仅在命令对象中短暂存在，绝不写入日志或审计正文。
 */
public final class IdentityCommands {

    private IdentityCommands() {
    }

    /**
     * 创建本地用户命令。
     */
    public record CreateUserCommand(String username,
                                    String displayName,
                                    String email,
                                    String locale,
                                    String temporaryPassword,
                                    Set<String> scopes) {
    }

    /**
     * 更新本地用户资料命令。
     */
    public record UpdateUserCommand(String displayName, String email, String locale) {
    }

    /**
     * 注册 Agent 命令。
     */
    public record CreateAgentCommand(String displayName,
                                     String agentType,
                                     String vendor,
                                     int maxSensitivityLevel,
                                     Set<String> scopes) {
    }
}
