/*
 * 功能: platform-observability 模块说明——系统诊断、依赖健康摘要等可观测性能力。
 * 时间: 2026-06-29
 * 作者: AxeXie
 */

/**
 * 平台可观测性模块（platform-observability）。
 *
 * <p>提供面向运维诊断的只读能力，P0 阶段实现系统依赖与健康摘要 API。
 * 遵循模块分层：api 适配层 → application 应用服务 → domain 端口；infrastructure 实现依赖探测端口。
 */
package com.aihub.platform.observability;
