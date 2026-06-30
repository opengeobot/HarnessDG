# MCP 契约目录

> 依据：`prd/DEEP_RESEARCH_内部AI资产管理平台设计.md` 第 9 章、第 15.8 节
> 作者：AxeXie

本目录用于存放 MCP（Model Context Protocol）Tool 的 JSON Schema 与相关契约定义，作为
MCP Server、Agent 接入包（OpenClaw / QwenPaw Skill）与兼容测试的权威来源。

## 状态

P0-A 仅建立目录骨架。具体业务 Tool Schema 仍在 P4 Agent 接入阶段按"契约优先"流程填充，
但 P0-B 必须先提供统一 Principal/JWT、Scope/Permission、Tool Allowlist、审计、限流、错误响应
和 Trace 契约。MCP 实现不得再假定身份权限可后补。

## 规划内容

- `tools/`：各 MCP Tool 的 JSON Schema（名称、参数、返回结构、Scope、审批要求）。
  - 默认只读工具：`asset_search`、`asset_get`、`asset_list_versions`、`asset_get_version`、
    `asset_request_download`、`dataset_get_preview`。
  - 默认受限写工具：`asset_create_draft`、`asset_create_upload_session`、
    `asset_complete_upload`、`asset_submit_version`、`asset_publish_version`、
    `asset_deprecate_version`。

## 约束

- Tool 实现必须复用与 REST 相同的 Application Service，不得绕过权限或直接访问 Mapper。
- 优先使用平面对象、枚举与显式必填字段，避免复杂 `oneOf/anyOf`，以提升不同 Agent 运行时
  的兼容性。
- Tool 名称与 Schema 一经发布按 API 兼容策略维护；新增/变更 Tool 必须同步更新
  OpenClaw/QwenPaw 白名单示例与兼容测试。
- MCP 只接受平台签发的 Bearer JWT，调用前同时校验 JWT Scope、业务 Permission、资源状态和
  Agent Tool Allowlist；JWT 内角色不能替代实时授权。
- Tool 调用统一写访问日志和审计，禁止记录 JWT、预签名 URL、永久凭据或大体积参数。
