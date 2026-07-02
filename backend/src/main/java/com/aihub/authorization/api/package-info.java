/*
 * 功能: authorization 模块 api 子包说明。
 * 时间: 2026-06-30
 * 作者: AxeXie
 */

/**
 * authorization 模块的 api 适配层，承载角色/权限/绑定/ACL 的 REST 控制器。适配层仅做请求映射、授权
 * 校验与统一响应包装，业务规则在 application/domain 完成；不直接访问 Mapper，不返回持久化实体。
 */
package com.aihub.authorization.api;
