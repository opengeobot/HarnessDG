/*
 * 功能: authorization 模块 infrastructure 子包说明。
 * 时间: 2026-06-30
 * 作者: AxeXie
 */

/**
 * authorization 模块的 infrastructure 基础设施层，承载领域仓储端口的持久化实现（基于 JDBC 显式 SQL）
 * 与运行时引导器。权限解析、ACL 命中与下推查询均在数据库阶段完成，不做"先全量查再 Java 过滤"。
 */
package com.aihub.authorization.infrastructure;
