# P3 发布治理出口验收目录

> 状态：`READY`
> 依据：`p3-release-governance.md`、`user-journey-catalog.md`（JRN-P3-001..003）、`page-catalog.md`（PAGE-VER-*、PAGE-REV-*）。
> 前置：P2 版本与数据面 wiring committed；DEC-010/012 连续实施授权。

## 1. 出口规则

P3 只有在以下条件全部成立时才能退出：

1. 本目录所有 `MUST` 场景实际 PASS；
2. 没有 MUST 场景 SKIP；
3. 每个 PASS 绑定 Commit SHA、环境和 Evidence Manifest；
4. E3 场景使用真实 PostgreSQL/协议依赖，不以 Mock 替代核心状态机；
5. E4 场景从 Nginx/Browser/REST/Worker 入口执行到真实 Compose 服务；
6. 正常、失败、拒绝、漂移和恢复证据均存在；
7. OpenAPI、V17+ 迁移、事件、Worker、UI、Runbook 和追踪矩阵与被测 Commit 一致。

## 2. 校验管道

| 场景 ID | 优先级 | Given / When | 必须观察到 | 证据 |
| --- | --- | --- | --- | --- |
| `AC-P3-VAL-001` | MUST | 维护者将 DRAFT 推进到 VALIDATING 并重放校验 Job | 冻结 sourceCommit；结构化报告含 ruleId/severity/i18nKey/resourcePath；通过进入 PENDING_REVIEW 并持久化 policyVersion；失败回 DRAFT 保留报告；sourceCommit 变化使旧报告失效 | `E4` |

## 3. 提交与四眼审批

| 场景 ID | 优先级 | Given / When | 必须观察到 | 证据 |
| --- | --- | --- | --- | --- |
| `AC-P3-REV-001` | MUST | 提交人提交 PENDING_REVIEW 版本且审批人决策 | 仅接受最新 PASSED 校验报告；创建 PublishRequest 冻结 digest/commit；提交人不能审批自己；approve/reject 幂等；reject 回 DRAFT 且意见必填 | `E4` |
| `AC-P3-REV-002` | MUST | 审批前 sourceCommit 或 manifestDigest 已漂移 | submit/approve 拒绝并保留审计；旧审批无效；不得创建 Publish Saga | `E3` |

## 4. 发布 Saga 与 Gitea Tag

| 场景 ID | 优先级 | Given / When | 必须观察到 | 证据 |
| --- | --- | --- | --- | --- |
| `AC-P3-PUB-001` | MUST | 已 APPROVED 的 PublishRequest 触发 VERSION_PUBLISH Job（Gitea enabled） | Gitea 创建受保护 Tag 指向冻结 Commit；PG 单事务保存 PUBLISHED 与三元组 tag/commitSha/manifestDigest；同 Commit 幂等；不同 Commit 永不覆盖并 FAILED | `E4` |
| `AC-P3-SAGA-001` | MUST | 注入 Tag 成功/PG 失败、Gitea 不可用、Tag 冲突等故障 | Saga 可查询/重试/补偿；不盲删 Tag；不确定超时先读 Gitea 核验；完整审计 | `E3` |

## 5. 审批与版本 UI

| 场景 ID | 优先级 | Given / When | 必须观察到 | 证据 |
| --- | --- | --- | --- | --- |
| `AC-P3-UI-001` | MUST | 审批人打开审批中心与版本详情 | 状态徽标完整；展示冻结 digest/commit 与校验报告摘要；Diff/Review 面板可比对漂移；Published 无覆盖入口；两语言 | `E4` |

## 6. 弃用与搜索降权

| 场景 ID | 优先级 | Given / When | 必须观察到 | 证据 |
| --- | --- | --- | --- | --- |
| `AC-P3-DEP-001` | MUST | 授权用户对 PUBLISHED 版本执行 deprecate/archive | Tag/Commit/Manifest 不变；搜索降权；已授权仍可下载；状态与原因审计 | `E3` |

## 7. Compose 纵向出口

| 场景 ID | 优先级 | Given / When | 必须观察到 | 证据 |
| --- | --- | --- | --- | --- |
| `AC-P3-EXIT-001` | MUST | 干净 Compose 下执行 validate→submit→approve→publish→deprecate 闭环 | JRN-P3-001..003 PASS；三元组 100% 完整；提交人自审拒绝；覆盖 Tag 各入口失败；Evidence 绑定同一 Commit | `E4` |
