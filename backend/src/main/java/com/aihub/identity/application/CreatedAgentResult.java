/*
 * 功能: 新建 Agent 结果视图，对应契约 CreatedAgent；凭据明文仅在创建响应返回一次。
 * 时间: 2026-06-30
 * 作者: AxeXie
 */
package com.aihub.identity.application;

/**
 * 新建 Agent 结果视图。
 *
 * <p>{@code credential} 为一次性返回的凭据明文，服务端只保存其摘要；该字段绝不写入日志或审计正文。
 *
 * @param agent      Agent 视图
 * @param credential 一次性凭据明文
 */
public record CreatedAgentResult(AgentView agent, String credential) {
}
