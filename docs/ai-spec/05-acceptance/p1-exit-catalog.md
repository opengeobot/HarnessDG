# P1 资产目录出口验收目录

> 状态：`READY`
> 依据：`p1-asset-catalog.md`、`user-journey-catalog.md`（JRN-P1-001..004）、`page-catalog.md`（PAGE-AST-001..006）。
> 前置：P0-B VERIFIED；P0BR-046 解除 P1 门禁。
> 数据集专项 AC：`dataset-agent-exit-catalog.md` 中 `AC-DST-TAX-*`、`AC-DST-DETAIL-*`、`AC-DST-DISC-*` 由 TASK-P1-010..012 引用，不在此重复定义。

## 1. 出口规则

P1 只有在以下条件全部成立时才能退出：

1. 本目录所有 `MUST` 场景实际 PASS；
2. 没有 MUST 场景 SKIP；
3. 每个 PASS 绑定 Commit SHA、环境和 Evidence Manifest；
4. E3 场景使用真实 PostgreSQL/协议依赖，不以 Mock 替代；
5. E4 场景从 Nginx/Browser/Client 入口执行到真实 Compose 服务；
6. 正常、失败、拒绝和恢复证据均存在；
7. OpenAPI、V27+ 迁移、事件、UI、Runbook 和追踪矩阵与被测 Commit 一致；
8. `AC-DST-TAX-*`、`AC-DST-DETAIL-*`、`AC-DST-DISC-*` 在 TASK-P1-010..012 中 PASS。

## 2. 坐标、类型与责任模型

| 场景 ID | 优先级 | Given / When | 必须观察到 | 证据 |
| --- | --- | --- | --- | --- |
| `AC-P1-AST-001` | MUST | 审查 MODEL/DATASET 坐标、Owner、别名和可见性契约与 Schema | assetId 与 `aih://{namespace}/{type}/{name}` 分离；`(namespace,type,name)` 唯一；至少一个 Team Owner；重命名保留 Alias；MODEL/DATASET 扩展字段使用 itemCode/tagId；OpenAPI/Domain Invariants 一致 | `E3` |

## 3. 创建与 Gitea 建仓

| 场景 ID | 优先级 | Given / When | 必须观察到 | 证据 |
| --- | --- | --- | --- | --- |
| `AC-P1-AST-002` | MUST | 授权维护者提交合法 CreateAssetRequest 并触发 Provision Saga | 意图持久化；Worker 创建 Gitea 仓库；初始 asset.yaml/README/保护/Webhook 与请求一致；Provision 完成后可搜索；审计和任务状态可追踪 | `E4` |
| `AC-P1-AST-003` | MUST | 相同 Idempotency-Key 和请求摘要重放创建 | 返回首次状态码/响应；不重复建仓/资产；审计不重复 | `E4` |
| `AC-P1-AST-004` | MUST | Gitea 在 Provision 窗口暂时不可用或超时 | 保留可重试意图或返回稳定失败；恢复后 Saga 完成或进入人工状态；不返回伪成功 | `E4` |
| `AC-P1-AST-005` | MUST | 无 asset:create、跨作用域或非法治理值的创建请求 | 统一拒绝；不产生资产/Gitea/对象副作用；防枚举语义 | `E4` |

## 4. 搜索与防枚举

| 场景 ID | 优先级 | Given / When | 必须观察到 | 证据 |
| --- | --- | --- | --- | --- |
| `AC-P1-AST-006` | MUST | 两个权限不同的 Principal 对同一筛选/Cursor 查询 | SQL 同时应用 Role/Scope/Membership/ACL/Visibility 过滤；Cursor 绑定 filter/sort/Principal；只返回有权 AssetSummary | `E4` |
| `AC-P1-AST-007` | MUST | 无权主体查询私有/不可见资产及 Facet/count/autocomplete | 列表/详情/聚合均不泄漏名称、数量或存在性；成功路径证明系统不是全部拒绝 | `E4` |

## 5. 详情与 Card 投影

| 场景 ID | 优先级 | Given / When | 必须观察到 | 证据 |
| --- | --- | --- | --- | --- |
| `AC-P1-AST-008` | MUST | 有权主体打开资产详情并查看 Card 与 DEC-014 快速使用面板 | 授权 Predicate 与搜索一致；Card 来自锁定 sourceCommit；DISABLED 治理项带标记；快速使用片段不含 Token/永久凭据；Markdown 安全渲染 | `E4` |

## 6. 更新与别名

| 场景 ID | 优先级 | Given / When | 必须观察到 | 证据 |
| --- | --- | --- | --- | --- |
| `AC-P1-AST-009` | MUST | 两个并发更新或重命名携带不同 rowVersion/Commit | 只有一个成功；冲突返回 CONCURRENT_MODIFICATION；新坐标预留且旧坐标进入 Alias；Gitea/投影通过 Saga 一致 | `E4` |

## 7. Owner、Maintainer 与 ACL

| 场景 ID | 优先级 | Given / When | 必须观察到 | 证据 |
| --- | --- | --- | --- | --- |
| `AC-P1-AST-010` | MUST | Owner 更换、Maintainer 绑定、资产 ACL 授予/撤销及 Team 成员变化 | 至少一个有效 Team Owner；不能移除最后 Owner；ACL 不突破 Scope/Sensitivity；权限来源可解释；变更审计并按策略通知 | `E4` |

## 8. 弃用、归档与恢复

| 场景 ID | 优先级 | Given / When | 必须观察到 | 证据 |
| --- | --- | --- | --- | --- |
| `AC-P1-AST-011` | MUST | 对 ACTIVE 资产执行 deprecate/archive/restore 且存在 Published Version | 状态转换符合 AssetStatus 规则；归档默认搜索隐藏；恢复不改变已发布 Version Commit/Card；引用检查阻止非法删除 | `E4` |

## 9. Gitea 对账

| 场景 ID | 优先级 | Given / When | 必须观察到 | 证据 |
| --- | --- | --- | --- | --- |
| `AC-P1-AST-012` | MUST | Reconciler 发现 repo 漂移、Webhook 重复 delivery 或外部手工权限变化 | 可确定漂移自动修复并审计；重复 delivery 幂等；外部权限不反向修改业务 ACL；Noop Provisioner 不在可部署 Profile | `E4` |

## 10. 前端页面与国际化

| 场景 ID | 优先级 | Given / When | 必须观察到 | 证据 |
| --- | --- | --- | --- | --- |
| `AC-P1-AST-013` | MUST | 两主体访问 PAGE-AST-001..006 并切换 zh/en | 创建/列表/详情/设置/访问/血缘页面具备 Loading/Empty/Error/Retry/Success；受控选择；权限按钮与后端一致；Provisioning 状态可见；组件测试和浏览器 E2E PASS | `E4` |

## 11. Compose 纵向旅程

| 场景 ID | 优先级 | Given / When | 必须观察到 | 证据 |
| --- | --- | --- | --- | --- |
| `AC-P1-AST-014` | MUST | 干净 Compose 下两个权限不同 Principal 执行 JRN-P1-001..004 | 创建→搜索→详情→更新→弃用/归档/恢复全链 PASS；无权主体在所有入口无泄漏；Evidence 绑定同一 Commit | `E4` |

## 12. 数据集场景引用

TASK-P1-010..012 分别引用 `dataset-agent-exit-catalog.md` 中的：

- `AC-DST-TAX-001..004`
- `AC-DST-DETAIL-001..004`
- `AC-DST-DISC-001..005`

不在本目录重复定义上述 ID。
