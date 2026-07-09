# P4 Agent 接入出口验收目录

> 状态：`READY`
> 依据：`p4-agent-integration.md`、`user-journey-catalog.md`（JRN-P4-001..005）、`dataset-experience.md`（DEC-008）。
> 前置：P3 发布治理 gap closure committed；DEC-010 连续实施授权。
> 数据集专项 AC：`dataset-agent-exit-catalog.md` 中 `AC-DST-AI-*`、`AC-DST-AIW-*` 由 TASK-P4-008/009/010 引用，不在此重复定义。

## 1. 出口规则

P4 只有在以下条件全部成立时才能退出：

1. 本目录所有 `MUST` 场景实际 PASS；
2. 没有 MUST 场景 SKIP；
3. 每个 PASS 绑定 Commit SHA、环境和 Evidence Manifest；
4. E3 场景使用真实 PostgreSQL/协议依赖，不以 Mock 替代；
5. E4 场景从 MCP Client、Agent OpenAPI、Skill 或 Browser 入口执行到真实 Compose 服务；
6. 正常、失败、拒绝和恢复证据均存在；
7. OpenAPI、MCP tools.yaml、Agent API、Skill 包、Runbook 和追踪矩阵与被测 Commit 一致；
8. `AC-DST-AI-*`、`AC-DST-AIW-*` 在 TASK-P4-008/009/010 中 PASS。

## 2. MCP Tool Catalog 与 Schema

| 场景 ID | 优先级 | Given / When | 必须观察到 | 证据 |
| --- | --- | --- | --- | --- |
| `AC-P4-SCH-001` | MUST | tools.yaml 与 McpToolCatalog 同步；每个 Tool 声明 read/write、Scope、Permission、Schema 和错误映射 | 阶段标记为 P4；write 工具默认关闭；`additionalProperties:false`；分页/条目/执行时间上限文档化；AUD-011 无漂移 | `E3` |

## 3. MCP Streamable HTTP

| 场景 ID | 优先级 | Given / When | 必须观察到 | 证据 |
| --- | --- | --- | --- | --- |
| `AC-P4-MCP-001` | MUST | 持有平台 JWT 的 Agent 客户端对 `/mcp` 执行 initialize、tools/list、tools/call | Bearer JWT 建立与 REST 相同 PrincipalContext；协议错误不返回 HTTP 200 伪成功；Nginx 对 `/mcp` 禁用缓冲；日志不含 Authorization/预签名 URL；客户端断开不丢失已提交可靠写 | `E4` |

## 4. 只读搜索与详情 Tools

| 场景 ID | 优先级 | Given / When | 必须观察到 | 证据 |
| --- | --- | --- | --- | --- |
| `AC-P4-MCP-003` | MUST | 授权 Agent 调用 asset_search/asset_get | 复用 REST Application Query；DATASET 输入输出复用 REQ-DST-TAX-001/REQ-DST-AI-001；返回 matchedFields 和精确 latestPublished；无结果不暴露不可见数量；无权 fail closed | `E4` |

## 5. 精确版本与下载 Tools

| 场景 ID | 优先级 | Given / When | 必须观察到 | 证据 |
| --- | --- | --- | --- | --- |
| `AC-P4-MCP-004` | MUST | Agent 调用 asset_list_versions、asset_get_version、asset_request_download | 不臆测版本；download 返回 downloadHandle/expiresAt/digest 而非预签名 URL；DEPRECATED 警告；ARCHIVED 默认不可见；审计不含 URL | `E4` |

## 6. Tool Allowlist 与双重授权

| 场景 ID | 优先级 | Given / When | 必须观察到 | 证据 |
| --- | --- | --- | --- | --- |
| `AC-P4-ALW-001` | MUST | 两个 Agent（只读/写）与无权主体尝试 list/call 隐藏或高风险 Tool | 同时校验 mcp:invoke、Scope、Permission、iam_agent_tool allowlist、资源策略；list 与 call 双控；拒绝产生 AGENT_ACCESS_DENIED 审计；不泄露不可见资源 | `E4` |

## 7. Agent 友好 OpenAPI

| 场景 ID | 优先级 | Given / When | 必须观察到 | 证据 |
| --- | --- | --- | --- | --- |
| `AC-P4-API-001` | MUST | 导入只读与 contribution 两个 Agent OpenAPI Profile | 从权威 OpenAPI 裁剪生成；默认只读包不含 write 端点；contribution profile 仅含草稿/上传/complete/状态；相同 JWT 得到与 REST/MCP 等价授权和业务结果；breaking diff 阻断 | `E4` |

## 8. Skill 接入包

| 场景 ID | 优先级 | Given / When | 必须观察到 | 证据 |
| --- | --- | --- | --- | --- |
| `AC-P4-SKL-001` | MUST | OpenClaw 与 QwenPaw 加载 aihub-agent-integration Skill 包 | compatibility.yaml 记录锁定客户端版本；SKILL.md 规则覆盖搜索→精确版本→下载校验；Secret 使用 Secret Store 引用；README/Card 标注不可信 | `E4` |

## 9. 30 分钟接入

| 场景 ID | 优先级 | Given / When | 必须观察到 | 证据 |
| --- | --- | --- | --- | --- |
| `AC-P4-COMP-001` | MUST | 从无配置客户端开始，管理员注册只读 Agent 并完成搜索→下载→校验 | 全流程 ≤30 分钟（不含大型模型下载）；Token/credential/URL 不出现在聊天/日志/Workspace/历史；access 过期/刷新策略正常；两个锁定客户端分别生成 Evidence | `E4` |

## 10. MCP Resources（SHOULD）

| 场景 ID | 优先级 | Given / When | 必须观察到 | 证据 |
| --- | --- | --- | --- | --- |
| `AC-P4-RES-001` | SHOULD | Agent 读取 aih://asset/* Resource URI | 每次 read 实时授权；URI 不构成能力票据；Card 标注不可信并限制大小；内容带精确 sourceCommit/version/digest；缓存不跨 Principal | `E4` |

## 11. Compose 纵向出口

| 场景 ID | 优先级 | Given / When | 必须观察到 | 证据 |
| --- | --- | --- | --- | --- |
| `AC-P4-EXIT-001` | MUST | 干净 Compose 下执行只读 Agent 搜索→精确版本→本地下载校验，以及显式授权写 Agent 创建→上传→Worker→状态查询 | JRN-P4-001..005 全链 PASS；MCP 下载只返回 handle；写 Agent 不能自动发布；Evidence 绑定同一 Commit | `E4` |

## 12. 数据集场景引用

TASK-P4-008 引用 `dataset-agent-exit-catalog.md` 中的：

- `AC-DST-AI-001..005`

TASK-P4-009 引用 `dataset-agent-exit-catalog.md` 中的：

- `AC-DST-AIW-001..002`

TASK-P4-010 引用 `dataset-agent-exit-catalog.md` 中的：

- `AC-DST-AIW-003..006`

不在本目录重复定义上述 ID。
