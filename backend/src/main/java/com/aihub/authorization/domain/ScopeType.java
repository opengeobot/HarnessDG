/*
 * 功能: 授权作用域类型枚举，对应契约 ScopeType（角色绑定/请求作用域维度）。
 * 时间: 2026-06-30
 * 作者: AxeXie
 */
package com.aihub.authorization.domain;

/**
 * 授权作用域类型。
 *
 * <p>用于角色绑定与授权请求的作用域维度判定：{@code PLATFORM} 平台级、{@code ORGANIZATION} 组织级、
 * {@code PROJECT} 项目级、{@code ASSET} 资源级（资源 ACL）。
 */
public enum ScopeType {

    /** 平台级。 */
    PLATFORM,

    /** 组织级。 */
    ORGANIZATION,

    /** 项目级。 */
    PROJECT,

    /** 资源级（资源 ACL）。 */
    ASSET
}
