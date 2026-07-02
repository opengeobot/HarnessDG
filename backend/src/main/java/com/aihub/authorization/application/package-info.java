/*
 * 功能: authorization 模块 application 子包说明。
 * 时间: 2026-06-30
 * 作者: AxeXie
 */

/**
 * authorization 模块的 application 应用层，编排统一授权判定（AuthorizationService）与角色/绑定/ACL
 * 管理用例，向 REST/MCP/Worker 适配层暴露稳定的授权 API。应用层依赖 domain 端口，不直接访问 Mapper。
 */
package com.aihub.authorization.application;
