# P2 版本与数据面出口验收目录

> 状态：`READY`
> 依据：`p2-version-transfer.md`、`user-journey-catalog.md`（JRN-P2-001..004）、`page-catalog.md`（PAGE-UPL-*、PAGE-VER-*）。
> 前置：P1 资产目录 gap closure committed；DEC-010 连续实施授权。
> 数据集专项 AC：`dataset-agent-exit-catalog.md` 中 `AC-DST-PRE-*`、`AC-DST-CLI-*` 由 TASK-P2-008/009 引用，不在此重复定义。

## 1. 出口规则

P2 只有在以下条件全部成立时才能退出：

1. 本目录所有 `MUST` 场景实际 PASS；
2. 没有 MUST 场景 SKIP；
3. 每个 PASS 绑定 Commit SHA、环境和 Evidence Manifest；
4. E3 场景使用真实 PostgreSQL/协议依赖，不以 Mock 替代；
5. E4 场景从 Nginx/Browser/CLI/Client 入口执行到真实 Compose 服务；
6. 正常、失败、拒绝和恢复证据均存在；
7. OpenAPI、V16+ 迁移、事件、Worker/CLI、UI、Runbook 和追踪矩阵与被测 Commit 一致；
8. `AC-DST-PRE-*`、`AC-DST-CLI-*` 在 TASK-P2-008/009 中 PASS。

## 2. 版本草稿

| 场景 ID | 优先级 | Given / When | 必须观察到 | 证据 |
| --- | --- | --- | --- | --- |
| `AC-P2-VER-001` | MUST | 授权维护者在 ACTIVE Asset 下创建 DRAFT Version 并重放 Idempotency-Key | `(assetId,version)` 唯一；绑定 sourceCommit/创建者/rowVersion；同键重放返回同一 versionId；冲突返回 ASSET_VERSION_CONFLICT；无权/非法状态 fail closed | `E4` |

## 3. Manifest 摘要

| 场景 ID | 优先级 | Given / When | 必须观察到 | 证据 |
| --- | --- | --- | --- | --- |
| `AC-P2-MNF-001` | MUST | Golden fixtures 覆盖顺序、换行、Unicode、空字段、大整数和恶意 path | Java/Worker/CLI 参考实现得到相同 bytes/digest；修改受保护字段改变 digest；规范化文档与 Schema 版本化 | `E3` |

## 4. 上传 Session

| 场景 ID | 优先级 | Given / When | 必须观察到 | 证据 |
| --- | --- | --- | --- | --- |
| `AC-P2-UPL-001` | MUST | 授权用户提交合法 Upload Session 创建请求 | Asset/Version/权限/配额/路径校验；PG Session 与 MinIO prefix 创建；返回 sessionId/过期/Part 策略无永久凭据；同幂等返回同一 Session；超限返回 LIMIT_EXCEEDED 并引导 CLI/DVC | `E4` |

## 5. Multipart Part 签名与 complete

| 场景 ID | 优先级 | Given / When | 必须观察到 | 证据 |
| --- | --- | --- | --- | --- |
| `AC-P2-UPL-002` | MUST | Session owner 请求 Part 签名、并行上传并完成 complete | URL 最小权限短 TTL；complete 核对 MinIO metadata 并创建唯一 Materialization Job；同 complete 重放返回同一 Job；不同清单冲突；日志/响应不含完整预签名 URL | `E4` |

## 6. 物化 Worker

| 场景 ID | 优先级 | Given / When | 必须观察到 | 证据 |
| --- | --- | --- | --- | --- |
| `AC-P2-UPL-003` | MUST | Materialization Worker 处理 complete 后的 Session 并注入故障 | 流式校验 size/SHA-256/路径；DVC add/push 与 Git commit/push 成功；Artifact 投影与 manifestDigest 保存；Session 完成；崩溃/依赖失败可恢复且无重复 Commit | `E4` |

## 7. 上传前端恢复

| 场景 ID | 优先级 | Given / When | 必须观察到 | 证据 |
| --- | --- | --- | --- | --- |
| `AC-P2-UPL-004` | MUST | 浏览器 Multipart 上传中刷新、暂停/恢复或单 Part 失败 | 刷新后从服务端对账已完成 Parts；只重签/重传失败 Part；不保存 Token/预签名 URL；complete 后显示 Worker 阶段进度；两语言与错误/过期/越权状态完整 | `E4` |

## 8. 下载票据

| 场景 ID | 优先级 | Given / When | 必须观察到 | 证据 |
| --- | --- | --- | --- | --- |
| `AC-P2-DL-001` | MUST | 获权/无权主体请求下载票据并等待过期 | 权限/状态/敏感度/用途检查；P2 草稿仅维护者可下；返回 PRESIGNED_URL 或 GIT_DVC 含 expiresAt/digest；授权 100% 审计且日志脱敏；过期/撤销后行为符合契约 | `E4` |

## 9. CLI DVC 往返

| 场景 ID | 优先级 | Given / When | 必须观察到 | 证据 |
| --- | --- | --- | --- | --- |
| `AC-P2-DVC-001` | MUST | 研发用户配置 Git/DVC 后 push 并清理本地再 pull | 受控 Git URL 与短期 DVC 凭据；dvc push/pull SHA-256 一致；凭据不入 Git/日志；权限撤销后不能访问未授权前缀 | `E4` |

## 10. Compose 纵向出口

| 场景 ID | 优先级 | Given / When | 必须观察到 | 证据 |
| --- | --- | --- | --- | --- |
| `AC-P2-EXIT-001` | MUST | 干净 Compose 下执行 Web 上传、Worker 物化、CLI/DVC 下载与校验 | JRN-P2-001..003 全链 PASS；文件 bytes 不经 Backend/Nginx；Secret/URL 不入 Git/浏览器存储/日志/审计；Evidence 绑定同一 Commit | `E4` |

## 11. Dataset 详情纵向旅程

| 场景 ID | 优先级 | Given / When | 必须观察到 | 证据 |
| --- | --- | --- | --- | --- |
| `AC-P2-JRN-001` | MUST | 数据使用者在 JRN-P2-004 下浏览 Dataset 详情并 CLI pull/resume/verify | Version/Files/Preview(Subset/Split/Stats)/CLI 同精确 Version 聚合；无权主体防枚举；摘要一致且无 Secret | `E4` |

## 12. 数据集场景引用

TASK-P2-008 引用 `dataset-agent-exit-catalog.md` 中的：

- `AC-DST-PRE-001..004`

TASK-P2-009 引用 `dataset-agent-exit-catalog.md` 中的：

- `AC-DST-CLI-001..006`

不在本目录重复定义上述 ID。
