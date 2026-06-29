# MCP 契约目录

> 依据：`prd/DEEP_RESEARCH_内部AI资产管理平台设计.md` 第 9 章、第 15.8 节
> 作者：AxeXie

本目录用于存放 MCP（Model Context Protocol）Tool 的 JSON Schema 与相关契约定义，作为
MCP Server、Agent 接入包（OpenClaw / QwenPaw Skill）与兼容测试的权威来源。

## 状态

P0 阶段仅建立目录骨架；具体 Tool Schema 在 **P1+（Agent 接入阶段）** 按"契约优先"流程填充。

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
