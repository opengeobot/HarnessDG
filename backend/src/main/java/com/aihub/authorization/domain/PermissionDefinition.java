/*
 * 功能: 权限定义领域值对象，对应 iam_permission 一行。
 * 时间: 2026-06-30
 * 作者: AxeXie
 */
package com.aihub.authorization.domain;

/**
 * 权限定义。
 *
 * @param code     权限编码（resource:action）
 * @param resource 资源域
 * @param action   操作
 */
public record PermissionDefinition(String code, String resource, String action) {
}
