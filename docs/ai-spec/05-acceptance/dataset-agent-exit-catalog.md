# 数据集体验与 AI 数据工作流验收目录

> 状态：`READY`
> 决策：`DEC-008`
> 说明：本目录定义 P1/P2/P4/P5 对数据集产品目标的 E4/E5 证据，不改变 P0-B 准入顺序。

## 1. 通用判定

1. 每个 MUST 场景必须绑定同一发布候选 Commit、环境、命令和 Evidence Manifest；
2. E4 从 Browser、CLI 或真实 MCP Client 入口到 PostgreSQL/Gitea/MinIO/DVC，不以 Mock 代替；
3. 两个权限不同的主体和一个受限 Agent 必须使用真实授权数据；
4. Token、永久对象凭据和预签名 URL 不得出现在对话、日志、审计、截图、CLI 参数或 Evidence；
5. REST、CLI、MCP 对相同 Principal/资源必须得到等价授权和业务结果；
6. 低阶段场景通过不能替代高阶段出口；SKIP 永远不是 PASS。

## 2. 分类、标签与发现

| 场景 ID | Kind | Given / When | 必须观察到 | 证据 |
| --- | --- | --- | --- | --- |
| `AC-DST-TAX-001` | NORMAL | 创建带 task/modality/format/language/license/sensitivity/tagId 的 DATASET 后按组合条件搜索 | 资产可被字段、全文和受控标签发现；返回稳定 code/ID、matchedFields 和正确排序 | E4 |
| `AC-DST-TAX-002` | UNAUTHORIZED | 两个 Principal 对同一筛选查询和 Facet 计数 | 每人只看见有权资产；不可见资产不影响 items、count、autocomplete 或 Cursor | E4 |
| `AC-DST-TAX-003` | BOUNDARY | 停用已被历史资产引用的字典项/标签后读取并尝试新建 | 历史回显带 disabled 标记；新引用被稳定错误拒绝并审计 | E4 |
| `AC-DST-TAX-004` | FAILURE | 提交展示名、自由标签、未知 code、非法 sort/cursor | 全部 fail closed；不写入自由值、不回退宽松搜索 | E3/E4 |

## 3. 详情、文件和讨论

| 场景 ID | Kind | Given / When | 必须观察到 | 证据 |
| --- | --- | --- | --- | --- |
| `AC-DST-DETAIL-001` | NORMAL | 打开有权数据集详情并切换 Published Version | Card、分类、Owner、Tag、Tag/Commit/Digest、Files、Schema/Split 使用同一精确 Version | E4 |
| `AC-DST-DETAIL-002` | BOUNDARY | latestPublished 改变或当前草稿 Card 更新 | 旧 Published 详情不漂移；URL、缓存和下载仍绑定选中 Version | E4 |
| `AC-DST-DETAIL-003` | UNAUTHORIZED | 无权主体访问列表、详情、文件、预览和讨论 | 使用防枚举语义；页面和 Facet 不泄露名称、数量或内容 | E4 |
| `AC-DST-DETAIL-004` | FAILURE | Gitea/Preview 服务暂时不可用 | 只返回带 sourceCommit/时间的合法投影或稳定错误；不显示伪造内容 | E4 |
| `AC-DST-DISC-001` | NORMAL | 有 `asset:discuss` 的用户创建 Thread、回复并 mention 有权用户 | 游标读取正确；通知/Outbox 原子产生；内容和审计可关联 | E4 |
| `AC-DST-DISC-002` | UNAUTHORIZED | 无 asset:read/discuss 或跨组织用户读写讨论 | 不可见/越权被防枚举拒绝；不产生回复、通知或泄漏 | E4 |
| `AC-DST-DISC-003` | CONCURRENCY | 相同 Idempotency-Key 重放回复并发生 rowVersion 冲突 | 只产生一个 Comment；冲突不覆盖修订历史 | E3/E4 |
| `AC-DST-DISC-004` | NORMAL | 作者修订/撤回，Moderator 隐藏/恢复/锁定 | Revision 保留；页面显示 Tombstone；权限、通知和审计正确 | E4 |
| `AC-DST-DISC-005` | FAILURE | 评论包含脚本、危险 HTML、外链诱导或 Prompt Injection | 安全渲染并标记不可信；不执行内容、不改变 Agent/Tool Policy | E4/E5 |

## 4. 安全预览

| 场景 ID | Kind | Given / When | 必须观察到 | 证据 |
| --- | --- | --- | --- | --- |
| `AC-DST-PRE-001` | NORMAL | 对支持格式的精确 Published Version 生成预览 | 持久化 Job 生成 Schema、Split、脱敏样例和统计；显示 source digest/时间 | E4 |
| `AC-DST-PRE-002` | BOUNDARY | CSV/JSONL/Parquet 达行列/大小上限或格式不支持 | 按确定性上限截断；不支持返回 PREVIEW_UNSUPPORTED_FORMAT，不伪造空成功 | E3/E4 |
| `AC-DST-PRE-003` | UNAUTHORIZED | 无权、超敏感等级或资产权限撤销后读取已有预览 | 实时拒绝；Preview Bucket 不公开；旧 URL/缓存不能绕过 | E4 |
| `AC-DST-PRE-004` | FAILURE | 数据包含 PII/Secret canary、压缩炸弹、恶意路径或损坏文件 | Job 安全失败或脱敏；预览、日志和审计中 canary 泄漏为 0 | E4/E5 |

## 5. CLI/API 搜索、下载与上传

| 场景 ID | Kind | Given / When | 必须观察到 | 证据 |
| --- | --- | --- | --- | --- |
| `AC-DST-CLI-001` | NORMAL | `aih dataset search --output json` 使用关键词和分类 | JSON 字段/排序与 REST 一致，只有授权数据 | E4 |
| `AC-DST-CLI-002` | NORMAL | pull 精确 Version 到空目录并启用 verify | 内容下载到 local-dir，Manifest/SHA-256 全部一致，退出码 0 | E4 |
| `AC-DST-CLI-003` | RECOVERY | 中断下载后以 include/exclude/resume 重试 | 只恢复目标文件，不越界，不重复完整下载，最终摘要正确 | E4 |
| `AC-DST-CLI-004` | UNAUTHORIZED | 凭据过期、权限撤销或请求私有数据集 | 稳定退出码和错误；本地无残留敏感半成品；不泄露资源 | E4 |
| `AC-DST-CLI-005` | NORMAL | 用受控 manifest 创建数据集并 push 小型多 Part Fixture | 创建、Session、complete、Worker 状态可追踪；新写值全部受控 | E4 |
| `AC-DST-CLI-006` | CONCURRENCY | create/push 网络超时、重放或同键不同请求 | 同意图不重复资产/Session/Commit；不同摘要返回冲突 | E4 |

## 6. AI 搜索、下载与贡献

| 场景 ID | Kind | Given / When | 必须观察到 | 证据 |
| --- | --- | --- | --- | --- |
| `AC-DST-AI-001` | NORMAL | 只读 Agent 用自然语言目标映射 query/filters 调 asset_search | 返回有权 DATASET 候选、matchedFields 和精确 latestPublished | E4 |
| `AC-DST-AI-002` | BOUNDARY | 多候选相近、无 Published 或用户未指定 Version | Agent 返回候选/请求选择，不臆测数据集或版本 | E4 |
| `AC-DST-AI-003` | NORMAL | Agent 选择精确 Version 并触发受信任本地 pull | 对话无 URL/Token；本地文件校验成功；Tool/CLI/下载审计可关联 | E4 |
| `AC-DST-AI-004` | UNAUTHORIZED | 缺 Tool/Scope/Permission、超敏感或调用隐藏 Tool | list/call 双重拒绝并审计，不泄露不可见资源和高风险工具 | E4 |
| `AC-DST-AI-005` | FAILURE | 下载校验失败、handle 过期或依赖临时失败 | 不报告成功；只对 retryable 错误退避；重新授权不复用旧 URL | E4 |
| `AC-DST-AIW-001` | NORMAL | 显式授权写 Agent 提交有效 itemCode/tagId/Owner 创建草稿 | 返回同一资产/Job；Gitea 初始 Card 与投影一致；完整审计 | E4 |
| `AC-DST-AIW-002` | UNAUTHORIZED | 默认只读 Agent 或跨组织 Agent 调创建/上传 | tools/list 不暴露或 call fail closed；无资产、Session 或对象副作用 | E4 |
| `AC-DST-AIW-003` | NORMAL | 写 Agent 创建 Session，由 CLI 数据通道上传并 complete | MCP 无二进制/URL；Worker 物化 DVC/Git；状态 Tool 返回非敏感进度 | E4 |
| `AC-DST-AIW-004` | CONCURRENCY | create/complete 超时重放、并发或同键不同摘要 | 无重复资产、Session、Job、Commit；冲突结果稳定 | E4 |
| `AC-DST-AIW-005` | FAILURE | 配额、非法路径、停用 tag、内容安全或 Worker 失败 | 稳定错误/任务状态/审计；失败可恢复且暂存对象按 Job 清理 | E4 |
| `AC-DST-AIW-006` | UNAUTHORIZED | 写 Agent 尝试 publish/delete/expand permission/configure | 平台可验证人工闸门前始终拒绝并产生高风险审计 | E4 |

## 7. 完整纵向出口

只有以下旅程全部 PASS 才能宣称满足本产品目标：

1. 人类：筛选发现 → Dataset Card → 精确 Version → Preview → Files → Discussion；
2. CLI/API：搜索 → 精确 pull → 中断恢复 → 校验；
3. 只读 AI：Tool 搜索 → 候选选择 → 精确版本 → 本地下载 → 校验；
4. 写 AI：受控元数据创建草稿 → 非 LLM 数据通道上传 → Worker 物化 → 提交人工审核；
5. 两个无权主体在所有入口均无法枚举或扩大可见性。

构建、Schema lint、单测或单独 401/403 均不能替代上述 E4。
