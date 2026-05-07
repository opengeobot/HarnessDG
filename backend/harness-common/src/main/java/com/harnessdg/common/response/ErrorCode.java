/**
 * 功能：统一错误码定义
 * 时间：2026-05-07
 * 作者：AxeXie
 */
package com.harnessdg.common.response;

import lombok.Getter;

@Getter
public enum ErrorCode {

    // 通用错误 1xxx
    SUCCESS(0, "success"),
    INTERNAL_ERROR(1000, "Internal server error"),
    BAD_REQUEST(1001, "Bad request"),
    VALIDATION_ERROR(1002, "Validation failed"),
    NOT_FOUND(1003, "Resource not found"),
    DUPLICATE(1004, "Resource already exists"),

    // 认证授权 2xxx
    UNAUTHORIZED(2001, "Unauthorized"),
    FORBIDDEN(2002, "Access denied"),
    TOKEN_EXPIRED(2003, "Token expired"),
    TOKEN_INVALID(2004, "Invalid token"),

    // 本体域 3xxx
    ENTITY_NOT_FOUND(3001, "Entity not found"),
    METRIC_NOT_FOUND(3002, "Metric not found"),
    DIMENSION_NOT_FOUND(3003, "Dimension not found"),
    RELATION_NOT_FOUND(3004, "Relation not found"),

    // 任务域 4xxx
    TASK_NOT_FOUND(4001, "Task not found"),
    TASK_ALREADY_RUNNING(4002, "Task is already running"),
    TASK_CANNOT_EXECUTE(4003, "Task cannot be executed in current status"),

    // Agent 域 5xxx
    AGENT_UNAVAILABLE(5001, "Agent service unavailable"),
    AGENT_TIMEOUT(5002, "Agent request timeout"),
    AGENT_ERROR(5003, "Agent processing error"),

    // 字典域 6xxx
    DICT_GROUP_NOT_FOUND(6001, "Dictionary group not found"),
    DICT_ITEM_NOT_FOUND(6002, "Dictionary item not found"),
    DICT_GROUP_EXISTS(6003, "Dictionary group code already exists"),
    DICT_SYSTEM_ITEM_CANNOT_DELETE(6004, "System dictionary item cannot be deleted"),

    // 用户/角色/权限 7xxx
    USER_NOT_FOUND(7001, "User not found"),
    USERNAME_EXISTS(7002, "Username already exists"),
    EMAIL_EXISTS(7003, "Email already exists"),
    OLD_PASSWORD_INCORRECT(7004, "Old password incorrect"),
    ROLE_NOT_FOUND(7005, "Role not found"),
    ROLE_CODE_EXISTS(7006, "Role code already exists"),
    SYSTEM_ROLE_CANNOT_MODIFY(7007, "System role cannot be modified"),
    SYSTEM_ROLE_CANNOT_DELETE(7008, "System role cannot be deleted"),

    // 配置域 8xxx
    CONFIG_NOT_FOUND(8001, "Config not found"),
    CONFIG_KEY_EXISTS(8002, "Config key already exists"),
    CONFIG_READONLY(8003, "Config is read only");

    private final int code;
    private final String message;

    ErrorCode(int code, String message) {
        this.code = code;
        this.message = message;
    }
}
