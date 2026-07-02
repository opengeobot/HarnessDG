/*
 * 功能: organization 模块 infrastructure 子包说明。
 * 时间: 2026-06-29
 * 作者: AxeXie
 */

/**
 * organization 模块的 infrastructure 基础设施层。
 *
 * <p>基于 {@code NamedParameterJdbcTemplate} 实现仓储端口与跨模块出站端口
 * {@code OrganizationMembershipPort}（同一适配器实现，供 authorization 接通 AccessScope）。
 * 列表查询在数据库阶段下推过滤，不"先全量再 Java 过滤"。
 */
package com.aihub.organization.infrastructure;
