---
schemaVersion: harnessdg.task/v1
taskId: TASK-P0BR-080
status: DRAFT
implementationAuthorized: false
phase: P1
baseCommit: a0227961e085a6feae627bff7fcc276937b63b6b
stageGatePassed: false
stageGateEvidence: AI 起草 DRAFT（2026-10-10，DEC-009：AI 不得自批、不得回写审批位）。
  差距事实链（2026-10-10 以 baseCommit 复证）：表 asset_subscription（V15，ux UNIQUE
  (asset_id, principal_id)）仅被讨论域写入（DiscussionController POST/GET
  /assets/{assetId}/discussions/subscription，布尔 toggle 语义）；通知侧已联动
  （VersionApplicationService L168-178 发布时经 NotificationRecipientResolver
  .resolveSubscribers fan-out VERSION_PUBLISHED）；物理回收已级联
  （PhysicalRecycleJobHandler L177 DELETE FROM asset_subscription）。缺口=独立订阅资源面：
  无个人订阅列表、退订非幂等（toggle）、无 UI 管理入口。规格增补已于本起草轮落盘：
  REQ-DST-SUB-001（status: PROPOSED）、PAGE-COM-004、AC-DST-SUB-001..003
  （validate-spec PASS，148 docs / 749 IDs）。
  本卡生效前置（缺一不得开工）：① 批准人评审 REQ-DST-SUB-001 并置 READY（含裁定：
  订阅列表是否附最近 VERSION_PUBLISHED 通知时间可选字段）；② 079 合入后串行开工
  （两卡共享 AssetDetailPage/i18n/e2e/契约/routes/navigation 面）；③ 批准人回写本卡审批位
  并按届时 HEAD 更新 baseCommit、stageGatePassed 置 true。
stageGateEvidenceRefs:
  - docs/ai-spec/tasks/evidence/EVD-P0BR-077-001.yaml
  - docs/ai-spec/tasks/evidence/EVD-P0BR-078-001.yaml
requirements:
  - REQ-DST-SUB-001
acceptanceScenarios:
  - AC-DST-SUB-001
  - AC-DST-SUB-002
  - AC-DST-SUB-003
decisions:
  - DEC-008
scenarioEvidencePlan:
  - AC-DST-SUB-001|E4|真实 Compose 栈 Playwright + 后端测试：PUT 订阅 → GET 状态 true →
    该资产发布新版本后订阅者收件箱出现 VERSION_PUBLISHED 通知；讨论域布尔端点与独立端点
    读写同一状态（交叉断言）；审计 ASSET_SUBSCRIBED/UNSUBSCRIBED 可关联；退订后不再收后续通知
  - AC-DST-SUB-002|E4|后端契约测试 + Playwright：无 asset:read 主体 PUT → 防枚举拒绝；
    订阅后授权撤销 → /subscriptions 静默过滤不报错不泄露名称；发布 fan-out 对已失权订阅者
    在收件人解析处按实时授权拒绝（通知零投递、无泄漏）
  - AC-DST-SUB-003|E4|后端并发测试：重复 PUT 幂等（ux_subscription 单行、204）；对未订阅
    DELETE 幂等 204；PUT/DELETE 竞态终态一致；无多余审计事件
crossCuttingPlan:
  - AUTHN|复用既有 JWT 过滤器与 PrincipalContextHolder；新端点均为受保护业务接口，不新增
    匿名面；业务模块内不解析令牌
  - AUTHZ|订阅写入与列表读取前置实时 asset:read（复用 AssetAccessPolicy，fail closed 先于
    任何写入）；principal_id 一律取自当前 Principal；不新增权限位；通知投递侧在
    NotificationRecipientResolver/Notification 分发链路按实时授权过滤收件人（现状缺口，
    本卡补齐 AC-DST-SUB-002 语义）
  - DB_FILTER|GET /subscriptions 与资产列表同族权限谓词 SQL 下推（不做内存二次过滤），
    无权/已删资产静默过滤；游标分页复用既有 Cursor 实现
  - STATE|无状态机变更；订阅是布尔资源状态，非工作流状态
  - IDEMPOTENCY|PUT = 收敛为「已订阅」（INSERT ON CONFLICT DO NOTHING，204）；
    DELETE = 收敛为「未订阅」（影响行数 0 亦 204）；既有讨论域 POST toggle 端点保留为
    兼容别名并内部委托同一应用服务，禁止两套状态实现
  - CONSISTENCY|单 PostgreSQL 存储（asset_subscription 复用，不加列）；订阅写入与审计同事务；
    版本发布 fan-out 沿用既有 Outbox 链路不改语义
  - ERRORS|复用既有 ASSET_NOT_FOUND（防枚举）与 AUTH_PERMISSION_DENIED；本卡不新增
    ErrorCode 枚举项
  - AUDIT|新增审计动作类型 ASSET_SUBSCRIBED、ASSET_UNSUBSCRIBED（actionType 字符串先例，
    非 ErrorCode 枚举）；既有讨论域 toggle 端点委托新应用服务后同样产生该审计（行为增强
    登记于 EVD，不改变其响应契约）
  - NOTIFICATION|VERSION_PUBLISHED fan-out 既有实现保持；本卡补「收件人实时授权过滤」并加
    对应测试；不新增通知模板与渠道
  - TAXONOMY_I18N|订阅按钮/列表/空态文案 zh/en 对称（subscriptions.* 键族）；无字典/tag 引用
  - CONFIG|N/A - 无新配置项；不改 deploy/ 与 application.yml
  - OBSERVABILITY|沿用既有请求级指标与 Trace，不新增指标或告警
  - SECRETS|无凭据面；响应/日志不含 Token、内部 Endpoint、对象键
allowedPaths:
  - contracts/openapi/aihub-v1.yaml
  - backend/src/main/java/com/aihub/asset/subscription
  - backend/src/main/java/com/aihub/asset/discussion
  - backend/src/main/java/com/aihub/notification
  - backend/src/test
  - frontend/src/features/subscriptions
  - frontend/src/features/assets/AssetDetailPage.tsx
  - frontend/src/features/assets/DiscussionPanel.tsx
  - frontend/src/app/router/routes.tsx
  - frontend/src/app/layout/navigation.ts
  - frontend/src/shared/api/client.ts
  - frontend/src/shared/i18n
  - frontend/e2e
  - docs/ai-spec/02-delivery/work-breakdown.md
  - docs/ai-spec/04-ui/page-catalog.md
  - docs/ai-spec/tasks/TASK-P0BR-080.md
  - docs/ai-spec/tasks/evidence/EVD-P0BR-080-001.yaml
preExistingDirtyPaths: []
forbiddenPaths:
  - deploy
  - backend/src/main/resources/db/migration/V1__baseline.sql
  - backend/src/main/resources/db/migration/V2__asset_catalog.sql
  - backend/src/main/resources/db/migration/V15__discussion.sql
requiredEvidenceLevel: E4
evidenceTemplate: docs/ai-spec/templates/evidence-manifest.yaml
evidenceManifests:
  - docs/ai-spec/tasks/evidence/EVD-P0BR-080-001.yaml
requiredValidationCommands:
  - ./docs/ai-spec/tools/validate-spec.ps1
  - ./docs/ai-spec/tools/validate-task-card.ps1 -TaskPath docs/ai-spec/tasks/TASK-P0BR-080.md
  - ./docs/ai-spec/tools/validate-task-card.ps1 -TaskPath docs/ai-spec/tasks/TASK-P0BR-080.md -CheckChangedPaths
  - ./mvnw.cmd -q verify（backend 目录；覆盖订阅幂等/并发矩阵、防枚举、fan-out 实时授权过滤与讨论域别名等价测试）
  - pnpm lint（frontend 目录）
  - pnpm typecheck（frontend 目录）
  - pnpm test（frontend 目录；Vitest 含 SubscribeButton/SubscriptionsPage 用例）
  - pnpm build（frontend 目录）
  - pnpm test:e2e（frontend 目录；真实 Compose 栈 E4，订阅→发布→通知全链 + 退订不再收，既有套件无回归）
  - npx @redocly/cli@latest lint contracts/openapi/aihub-v1.yaml --config contracts/openapi/redocly.yaml
  - npx @redocly/cli@latest diff EVD-P0BR-080-base.yaml contracts/openapi/aihub-v1.yaml --fail-on-breaking（基线由 git show baseCommit 导出；上游不可得时按 067/068/077 先例等价论证，环境不可得登记 notProven）
approvedBy: <human-or-controlled-approval>
approvedAt: <UTC-timestamp>
---

# TASK-P0BR-080：资产订阅独立资源与通知联动收口（G3，草案待批准）

> 状态：`DRAFT`（AI 起草，2026-10-10；DEC-009——批准人评审 `REQ-DST-SUB-001` 置 READY 并回写本卡审批位前，禁止实施）
> 规则：一个任务只交付一个可独立验收的纵向结果。
> 门禁：YAML Front Matter 是机器权威；正文不得与其冲突。

## 1. 目标

把订阅从讨论域布尔 toggle 提升为独立资源：`PUT/DELETE/GET /assets/{assetId}/subscription`（幂等收敛语义）+ `GET /subscriptions` 个人订阅列表（`PAGE-COM-004`）+ 详情页订阅标记；讨论域既有端点委托同一应用服务（单一状态源）；版本发布 fan-out 补收件人实时授权过滤。修复 iteration-plan §4.3 复证的 G3 断链（通知侧已通、资源面与 UI 缺失）。

## 2. 关联规格

```yaml
requirements: [REQ-DST-SUB-001（PROPOSED，待人类评审——本卡生效前置）]
acceptanceScenarios: [AC-DST-SUB-001, AC-DST-SUB-002, AC-DST-SUB-003]
decisions: [DEC-008]
adrs: [ADR-0001]
openapiOperations: [新增 listSubscriptions, subscribeAsset, unsubscribeAsset, getAssetSubscription
  （既有 getDiscussionSubscription/toggle 保留为兼容别名，契约不变）]
mcpTools: []
eventSchemas: []
flywayBaseline: 无新增 Migration（复用 asset_subscription/V15，不加列；物理回收级联已存在）
```

## 3. 范围

### 允许修改

- 契约：`PUT/DELETE/GET /assets/{assetId}/subscription` + `GET /subscriptions`（游标分页）；
  既有讨论域订阅端点零 diff。登记 `x-implementation-status: implemented-unverified`。
- 后端：新包 `com.aihub.asset.subscription`（Controller 纯适配器 / ApplicationService /
  domain 仓储接口迁移承接）；`asset.discussion` 域改为委托新应用服务（删除重复状态逻辑，
  `JdbcDiscussionSubscriptionRepository` 归属调整）；`notification` 域收件人解析补实时授权过滤。
- 前端：`frontend/src/features/subscriptions`（SubscriptionsPage + SubscribeButton）；
  AssetDetailPage 挂订阅标记；DiscussionPanel 订阅开关改走新通道（UI 行为不变）；
  routes/navigation 增加 `/subscriptions` 入口；i18n zh/en。
- e2e：订阅→发布→通知 E4 全链 + 退订不再收 + 撤权过滤用例。
- 文档：本卡、EVD、work-breakdown 状态列、page-catalog `PAGE-COM-004` 实施注记。

### 明确不在范围

- 收藏（G4）→ 前序卡 `TASK-P0BR-079`；两卡共享面串行（079 先合入）。
- 通知渠道扩展（邮件/IM）、订阅摘要聚合推送、退订链接邮件；`asset_subscription` 表列变更。
- 不新增权限位、不新增 ErrorCode 枚举项、不改既有讨论端点契约。

### 禁止变化

- 讨论域 toggle 端点与独立资源端点必须共享同一状态源与同一应用服务，禁止两套写入路径漂移。
- `principal_id` 只从服务端 Principal 取得。
- 不改已应用 Migration（含 V15）；fan-out 既有 Outbox 语义不变，仅收紧收件人过滤。

## 4. 行为切片

| 场景 ID | Actor | Given | When | Then | 最低证据 |
| --- | --- | --- | --- | --- | --- |
| `AC-DST-SUB-001` | USER(有 asset:read) | 未订阅资产 | PUT → GET | 204 → true；讨论域 GET 同状态 | E4 |
| `AC-DST-SUB-001` | USER | 已订阅 | 该资产发布新版本 | 订阅者收件箱 VERSION_PUBLISHED；审计可关联 | E4 |
| `AC-DST-SUB-001` | USER | 已订阅 | DELETE 后再发布 | 无后续通知；GET false | E4 |
| `AC-DST-SUB-002` | USER(无 asset:read) | 他人私有资产 | PUT | 防枚举拒绝；零写入 | E2 |
| `AC-DST-SUB-002` | USER | 订阅后授权撤销 | GET /subscriptions + 发布 | 列表静默过滤；fan-out 该收件人零投递 | E4 |
| `AC-DST-SUB-003` | 并发 | 重复 PUT / 未订阅 DELETE / PUT+DELETE 竞态 | 收敛后查询 | 全部幂等 204；ux_subscription 单行；审计与状态变更一一对应 | E3 |

覆盖正常/边界/失败/越权/并发/回收（回收级联既有实现，回归断言并入 EVD）。

## 5. 横切要求

以 Front Matter `crossCuttingPlan` 13 键为准，不另行放宽。

## 6. 契约与数据先行

1. **OpenAPI**：新增 4 操作；既有讨论订阅端点零 diff → 非破坏取证必须 PASS。
2. **示例**：无既有 schema 变更。
3. **新增 Flyway 文件**：无（复用 V15 表；如评审裁定列表需附最近通知时间且需列/索引扩展，
   另行前向 Migration 并补正本卡）。
4. **存量清点与回填**：存量 `asset_subscription` 行即有效订阅，零回填。
5. **索引/约束**：`ux_subscription` 已存在；`(principal_id, created_at)` 列表索引仅在性能
   测试需要时以 V46+ 补建并登记。
6. **兼容窗口与失败恢复**：后端先发（旧前端 toggle 端点行为不变）；新前端遇旧后端 → 新端点
   404 → 按钮错误态回退；回滚顺序：前端 → 契约（表与数据不回滚）。
7. **生成类型更新**：前端 API 类型手工同步（同族口径）。

## 7. 实施步骤

- [ ] 契约及契约测试（OpenAPI 4 操作 + redocly lint；幂等/防枚举/别名等价矩阵）
- [ ] Migration：不适用（§6.3）
- [ ] Application 用例（订阅资源收敛语义 + 列表谓词下推；讨论域委托改造）
- [ ] REST Adapter + notification 收件人实时授权过滤
- [ ] 前端 SubscriptionsPage/SubscribeButton + 详情页挂载 + DiscussionPanel 通道切换 +
      路由/导航 + i18n zh/en
- [ ] 正常/边界/失败/越权/并发测试（Vitest + 后端单测/并发/等价测试）
- [ ] Compose Fixture/Verify（E4 订阅→发布→通知全链 + 撤权过滤 + 退订停收）
- [ ] 文档同步（work-breakdown、page-catalog 注记、EVD）

勾选必须附 Evidence Manifest 条目；提交代码本身不能作为勾选依据。

## 8. 验证命令

```text
治理        validate-spec / validate-task-card / -CheckChangedPaths        各 exit 0
backend/    ./mvnw.cmd -q verify                                            exit 0   全 AC 后端切片
frontend/   pnpm lint; pnpm typecheck; pnpm test; pnpm build                exit 0   组件层
frontend/   pnpm test:e2e（真实 Compose 栈，凭据注入同 066/067/068 口径）    exit 0   E4 主证
contracts/  redocly lint + 非破坏取证（§FM 替代路径）                       exit 0   契约非破坏
```

SKIP 判定：任何因 Docker/凭据缺失导致的跳过视为 notProven，不构成 PASS（既有口径）。

## 9. 停止条件

- 批准人将 `REQ-DST-SUB-001` 评审判定为拒绝、或裁定列表需附字段但需表列扩展 → 停止回报
  （属规格/迁移补正，不在本卡擅改）；
- 讨论域 toggle 端点无法在不改其响应契约的前提下委托新应用服务 → 停止回报；
- 收件人实时授权过滤与既有 Outbox 语义冲突（需改发布链路事务）→ 停止回报；
- 开工时 079 未合入且共享面存在来源不明变更 → 不得开工；
- 需要改动 ErrorCode 枚举、权限口径或任何 forbiddenPaths 才能推进 → 停止回报等待补正裁定。

## 10. 完成报告

```text
Completed:
Changed files:
Contract/database changes:
Validation run and results:
Validation not run and reasons:
Remaining risks or follow-ups:
```

（DRAFT 轮留空；实施完成后按实际证据填写，成果表述在人工验收前一律 IMPLEMENTED_UNVERIFIED。）
