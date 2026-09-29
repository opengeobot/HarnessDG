---
schemaVersion: harnessdg.task/v1
taskId: TASK-P0BR-067
status: READY
implementationAuthorized: true
phase: P0-B
baseCommit: e9aa2a0af826ed3b37ea69288e48dcfe594c4ee9
stageGatePassed: true
stageGateEvidence: 上游 TASK-P0BR-066 已实施并提交（e9aa2a0，治理三连在交接时点全绿，
  EVD-P0BR-066-001 记录 E4 全量 97 passed / 1 skipped）。D-02 经用户在 2026-09-28 批准问句中
  明确「本卡移除 D-02，另立契约优先新卡」，并于 2026-09-29 确认「先提交 066，再起草 067」。
  缺陷事实链已在 066 §3/D-02 与 frontend/e2e/profile.spec.ts 的 test.fixme 注释中取证：
  GET /api/v1/me 契约与响应均无 username 字段，前端「登录名」位置实渲染 subject（=principalId）。
  2026-09-29 由用户在推进问句中选定「现在批准 067（推荐）」，同时裁定 §2 口径缺口走
  「按标签即承诺补 username 契约字段」路径（非「改标签」替代路径），本卡据此置 READY 并授权实施。
stageGateEvidenceRefs:
  - docs/ai-spec/tasks/evidence/EVD-P0BR-066-001.yaml
requirements:
  - REQ-IAM-002
  - REQ-UI-001
acceptanceScenarios:
  - AC-P0B-UI-002
  - AC-P0B-UI-003
decisions:
  - DEC-013
scenarioEvidencePlan:
  - AC-P0B-UI-002|E4|真实 Compose 栈：USER 主体在 /profile 看到本人登录名（username）而非 principalId；
    AGENT/CLIENT 主体 username 为 null 时页面回退展示且无裸 "null"/崩溃；无权限主体直接请求 /me 仍由后端拒绝
  - AC-P0B-UI-003|E4|redocly lint + blocking diff（CI 等价环境）证明 CurrentPrincipal 新增字段为纯可选、
    非破坏性；Playwright 解除 profile.spec.ts D-02 test.fixme 后断言 toHaveText(E2E_AUTHOR.username) 通过；
    后端契约测试（mvnw verify 子集）断言 /me 响应 USER 主体含 username、AGENT 主体为 null
crossCuttingPlan:
  - AUTHN|不新增/不改认证端点与 Token 生命周期；仅扩展 /me（getCurrentPrincipal）响应视图。access JWT 不进
    LocalStorage 的既有约束不变
  - AUTHZ|不改变任何权限口径；username 仅返回给已认证主体自身，无越权面扩大；严禁借机改 subject 语义
  - DB_FILTER|N/A - 不改任何查询与权限下推；username 取自既有 LocalUser.username 字段，无 schema 变更
  - STATE|N/A - 不改变 UserStatus/VersionStatus 等状态取值
  - IDEMPOTENCY|N/A - 纯读取端点扩展，无写操作
  - CONSISTENCY|N/A - 不跨系统写入
  - ERRORS|N/A - 不新增错误码；既有统一错误映射不变
  - AUDIT|N/A - /me 读取无新增审计事件类型；日志不得因新字段引入凭据类信息（username 非 Secret，
    但仍仅在响应体出现，不写入新日志行）
  - NOTIFICATION|N/A - 不触发通知
  - TAXONOMY_I18N|/profile「登录名」标签沿用既有 profile.loginName 键（zh/en 已对称）；若回退文案新增键则 zh/en 同步
  - CONFIG|前端 shared/types/auth.ts 的 CurrentPrincipal 接口必须与 OpenAPI schema 同步（统一生成口径或人工核对）
  - OBSERVABILITY|N/A - 不新增指标或告警
  - SECRETS|username 不是 Secret，但证据与日志不得含口令/Token；E2E 凭据继续取自环境变量
allowedPaths:
  - contracts/openapi/aihub-v1.yaml
  - backend/src/main/java/com/aihub/identity/application/CurrentPrincipalView.java
  - backend/src/main/java/com/aihub/identity/application/AuthenticationApplicationService.java
  - backend/src/main/java/com/aihub/identity/application/PrincipalQueryApplicationService.java
  - backend/src/main/java/com/aihub/identity/api/IdentityResponses.java
  - backend/src/test
  - frontend/src/shared/types/auth.ts
  - frontend/src/features/auth/ProfilePage.tsx
  - frontend/src/features/auth/ProfilePage.test.tsx
  - frontend/src/shared/i18n
  - frontend/e2e/profile.spec.ts
  - docs/ai-spec/02-delivery/work-breakdown.md
  - docs/ai-spec/tasks/TASK-P0BR-067.md
  - docs/ai-spec/tasks/evidence/EVD-P0BR-067-001.yaml
preExistingDirtyPaths:
  - docs/architecture/iteration-plan-2026-09-24.md|dc5f2e2137261cdd9fedafaa9b4daf327da27830c157ba1ec9f52b64dada155b
  - build-logs/verify-bom.ps1|b5003338d283e84b4b6dabaf0d9e47b6d90fe18b780d0fd2e59851d467ac9b3d
  - docs/architecture/test-report-2026-09-29.md|e60fc45360b43a263af73c68e60d656fca76f054509117dae1fdc887774220de
forbiddenPaths:
  - deploy
  - backend/src/main/resources/db/migration/V1__baseline.sql
  - backend/src/main/resources/db/migration/V2__asset_catalog.sql
  - backend/src/main/java/com/aihub/identity/domain
requiredEvidenceLevel: E4
evidenceTemplate: docs/ai-spec/templates/evidence-manifest.yaml
evidenceManifests:
  - docs/ai-spec/tasks/evidence/EVD-P0BR-067-001.yaml
requiredValidationCommands:
  - ./docs/ai-spec/tools/validate-spec.ps1
  - ./docs/ai-spec/tools/validate-task-card.ps1 -TaskPath docs/ai-spec/tasks/TASK-P0BR-067.md
  - ./docs/ai-spec/tools/validate-task-card.ps1 -TaskPath docs/ai-spec/tasks/TASK-P0BR-067.md -CheckChangedPaths
  - ./mvnw.cmd -q verify（backend 目录；覆盖 /me 契约测试与身份模块单测/集测）
  - pnpm lint（frontend 目录）
  - pnpm typecheck（frontend 目录）
  - pnpm test（frontend 目录；Vitest）
  - pnpm build（frontend 目录）
  - pnpm test:e2e（frontend 目录；真实 Compose 栈 E4，含解除 profile.spec.ts D-02 fixme）
  - npx @redocly/cli@latest lint contracts/openapi/aihub-v1.yaml --config contracts/openapi/redocly.yaml
  - npx @redocly/cli@latest diff EVD-P0BR-067-base.yaml contracts/openapi/aihub-v1.yaml --fail-on-breaking（EVD-P0BR-067-base.yaml 由 git show baseCommit:contracts/openapi/aihub-v1.yaml 导出为变更前基线，与 CI 的 /tmp/aihub-base.yaml 同构）
approvedBy: User
approvedAt: 2026-09-29T01:34:26Z
---

# TASK-P0BR-067：/profile 展示真实登录名（CurrentPrincipal 契约扩展 username）

> 状态：`READY`（用户已批准「现在批准 067」，见 Front Matter `approvedBy/approvedAt` 与 `stageGateEvidence`）
> 规则：一个任务只交付一个可独立验收的纵向结果。
> 门禁：YAML Front Matter 是机器权威；正文不得与其冲突。

## 1. 目标

已认证 USER 主体在 `/profile` 的「登录名」字段看到自己的登录名（username），而不是内部主体标识 principalId；`GET /api/v1/me` 契约与实现在 `CurrentPrincipal` 中提供可选 `username` 字段承载该信息，AGENT/CLIENT 主体为 `null` 且页面有明确回退。

## 2. 关联规格

```yaml
requirements: [REQ-IAM-002, REQ-UI-001]
acceptanceScenarios: [AC-P0B-UI-002, AC-P0B-UI-003]
decisions: [DEC-013]
adrs: [ADR-0001]
openapiOperations: [getCurrentPrincipal]
mcpTools: []
eventSchemas: []
flywayBaseline: V2（无新增 Migration）
```

- `REQ-IAM-002`（READY）的 `restOperations` 显式包含 `getCurrentPrincipal`，其「前置与输入」以 username 认证；本卡将同一主体的 username 回显于 /me，属该需求的最小信息闭环，不新增权限面。
- `REQ-UI-001`（READY）要求页面数据来自统一契约且「页面存在不构成完成」；`/profile` 现有渲染在标签语义下展示错误值，属该需求违背项。
- **设计口径缺口（须由批准人裁定）**：`page-catalog.md` PAGE-AUTH-002 对 `/profile` 的权威口径是「当前主体、角色/Scope 摘要、改密、登出」，未出现「登录名」字样；`ProfilePage.tsx:72` 的 `profile.loginName` 标签与 `profile.spec.ts` fixme 断言（`5ccbf47` 引入）先于口径登记存在。本卡按「标签即承诺」原则将「展示 username」补记为 PAGE-AUTH-002 的展示口径；批准人如认定应改标签为「主体标识」而非补字段，则本卡作废、D-02 以改标签方式关闭（该替代路径仅需前端卡，不需契约变更）。

## 3. 范围

### 允许修改

- 契约：`contracts/openapi/aihub-v1.yaml` 的 `CurrentPrincipal` schema（约 L3753-3772）新增可选 `username`（type string, nullable, maxLength 64，不进 `required`）。
- 后端：`CurrentPrincipalView` record 增 `username` 分量；`IdentityResponses.CurrentPrincipalPayload`（/me 与登录响应的实际序列化载体）增同名分量并在 `from(view)` 中透传；`AuthenticationApplicationService` 三处构造点（Agent 凭据交换 ≈L284、`currentPrincipal` AGENT 分支 ≈L324、`toPrincipalView` ≈L387）按主体类型填充（USER=`user.username()`，AGENT/CLIENT=`null`）。
- 前端：`shared/types/auth.ts` 接口、`ProfilePage.tsx` 渲染（`username ?? subject` 回退）、`e2e/profile.spec.ts` 解除 fixme。
- 文档：本卡、work-breakdown 状态列。

#### 批准前草案的两处事实校正（2026-09-29 实施期实地核对，已由用户批准补正 allowedPaths）

1. **`IdentityResponses.java` 为必经改动，草案漏列**。`GET /api/v1/me` 并不直接序列化 `CurrentPrincipalView`：`AuthController.me()`（L117-122）经 `IdentityResponses.CurrentPrincipalPayload.from(view)` 显式逐字段映射（L51-70）。若只改 view 而不改 payload，`username` 会在映射层被静默丢弃，字段永远不会出现在响应里。故 `allowedPaths` 增列该文件。
2. **`PrincipalQueryApplicationService` 实测与本卡无关**。该服务只产出 `PrincipalSummaryView`（`listPrincipals`/`existsByPrincipalId`），全文不含 `CurrentPrincipalView`；草案中「L33/L35 填充」一句源自 `profile.spec.ts` fixme 注释的错误描述。该路径保留在 `allowedPaths` 中但本次不改动（不改即不产生 diff），以免再次扩缩范围；真正的第三处填充点是 `IdentityResponses.from`。
3. 附带校正：`AuthControllerTest.tokenPair()`（L109）构造 `CurrentPrincipalView`，record 增分量后必须同步，属 `backend/src/test` 既有允许范围。
4. 附带校正（实施期）：`frontend/src/features/auth/ProfilePage.test.tsx` 为 §7「正常/边界测试（Vitest）」要求的组件断言载体（登录名回显 username、缺失回退 subject），草案漏列该路径，已补入 `allowedPaths`。

### 明确不在范围

- `subject` 字段语义**不变**（保持 principalId，供审计/ACL/TERM-001 口径消费）；不动 JWT claims；不改 Token 生命周期；不动 `deploy/`。

### 禁止变化

- 不允许把 `username` 塞进 `subject` 或替换 `subject`（破坏既有消费方与 TERM-001 语义统一）。
- 不允许新增必填字段或收紧任何 `required`（必须保持非破坏性 diff）。
- 不允许修改已应用的 Flyway Migration。

## 4. 行为切片

| 场景 ID | Actor | Given | When | Then | 最低证据 |
| --- | --- | --- | --- | --- | --- |
| `AC-P0B-UI-003` | USER | 正常 ACTIVE 用户已登录 | 访问 /profile 并请求 GET /me | 响应含 `username`=登录名；页面「登录名」显示该值 | `E4` |
| `AC-P0B-UI-003` | AGENT | Agent JWT（交换签发）主体 | 请求 GET /me | `username` 为 null/缺省；其余字段不回归 | `E3` |
| `AC-P0B-UI-002` | USER | 仅 notification:read 主体 | 访问 /profile | 「登录名」仍正确展示（本字段不受权限裁剪）；越权业务请求仍 403 | `E4` |
| `AC-P0B-UI-003` | USER | username 缺失（null） | 渲染 /profile | 回退展示主体标识，不出现裸 "null"、不崩溃 | `E2` |
| `AC-P0B-UI-003` | 匿名 | 无 Token | GET /me | 401，与现状一致（fail-closed 不回归） | `E3` |

## 5. 横切要求

以 Front Matter `crossCuttingPlan` 13 键为准，不另行放宽。

## 6. 契约与数据先行

1. **OpenAPI 变更**：`CurrentPrincipal` 增 `username: { type: string, nullable: true, maxLength: 64 }`，描述注明「仅 USER 主体返回其登录名；AGENT/CLIENT 为 null；不作为标识引用字段」。不进 `required` → 对既有客户端为纯增量，redocly `diff --fail-on-breaking` 必须 PASS。
2. **示例**：`/me` USER 响应示例补 `"username": "e2e_author"`；AGENT 分支示例保持无该字段或 null。
3. **新增 Flyway 文件**：无。username 已持久化于 `local_user.username`（V1 基线内）。
4. **存量清点与回填**：不适用（无历史数据迁移；响应字段为读时投影）。
5. **索引/约束**：不变。
6. **兼容窗口与失败恢复**：契约先行发布后，旧前端忽略新字段即无感；后端未升级而前端先升级时，`username ?? subject` 回退保证页面不坏。回滚顺序：前端 → 后端 → 契约（契约字段保留亦无害）。
7. **生成类型更新**：`frontend/src/shared/types/auth.ts` 的 `CurrentPrincipal` 手工同步增 `username?: string | null`；若仓内后续接 openapi-typescript 生成链则一并再生成。
8. **redocly 工具链口径更新（2026-09-29 实施期实测）**：
   - `current-state-audit-2026-07-02.md` L120 记载的本机 Node 25/Windows libuv assertion 缺陷已不存在：本机现为 node v20.19.2，与 CI（`.github/workflows/ci.yml` Node 20）等价，`npx @redocly/cli@latest lint` 本地即可给出 CI 等价 PASS（改动前后均 PASS，3 warnings 不阻断）。
   - **`redocly diff` 子命令已被上游移除**：@redocly/cli 2.55.0（@latest）/1.34.1/1.29.0/1.25.0（镜像源与官方源、全新安装均一致）不含 diff；仓内 CI 的 breaking-diff 步骤（ci.yml L147-162 使用 `@latest diff ... --fail-on-breaking`）因此是**既存缺陷**，PR-to-main 时该步骤会因 Unknown arguments 非零退出。本卡不改 CI（deploy/CI 不在 allowedPaths），仅如实申报。
   - **non-breaking 取证替代路径**：以 oasdiff v1.32.1（`oasdiff breaking <base> <revision>`）对 `git show baseCommit:` 导出的变更前基线执行对比，实测输出「No breaking changes to report」（exit 0），日志见 `build-logs/EVD-P0BR-067-oasdiff.txt`。本卡契约项 E4 以此 + lint PASS 为证据，不再依赖已失效的 redocly diff。

## 7. 实施步骤

- [x] 契约及契约测试（redocly lint + blocking diff；后端 /me 契约测试）
      —— `CurrentPrincipal.username` 可选字段入契约；redocly lint PASS（3 warnings 不阻断）
      + oasdiff breaking「No breaking changes to report」（§6.8 替代路径，CI redocly diff 既存缺陷见 EVD notProven）；
      后端 `AuthControllerTest`：USER 含 username、AGENT username=null、/me 匿名 401（E2）。日志 `067-mvnw-agent-test.log`。
- [x] Migration：不适用（§6.3，username 已在 V1 `local_user`，无 schema 变更）
- [x] Application/Domain 用例（四个构造点填充 + CurrentPrincipalView）
      —— `CurrentPrincipalView` 增 `username` 分量；`AuthenticationApplicationService`
      USER=`user.username()` / AGENT 分支=null / CLIENT=null 三处按主体类型填充。证据 EVD coverage.proven[3]。
- [x] REST Adapter（/me 序列化；MCP/Worker 不涉及）
      —— `IdentityResponses.CurrentPrincipalPayload.from(view)` 透传 username（卡 §3 校正 1 的必经改动）；
      MCP/Worker 未触及。E4 端到端经真实后端可观测（coverage.proven[2]）。
- [x] 前端类型、ProfilePage 回退渲染、i18n 核对
      —— `shared/types/auth.ts` 接口同步、`ProfilePage.tsx` 渲染 `username ?? subject` 回退；
      `profile.loginName` 键沿用（zh/en 对称、无新增键，§5 TAXONOMY_I18N）。
- [x] 正常/边界/失败/越权测试（Vitest + 后端测试 + Playwright 解除 fixme）
      —— Vitest 49 files/218 tests（含 `ProfilePage.test.tsx` username 回退用例）；后端契约测试 6/6；
      `profile.spec.ts` 解除 D-02 fixme + 新增 AC-P0B-UI-002 最小权限主体用例。
- [x] Compose Fixture/Verify（E4 全量 e2e 复跑，无回归）
      —— 真实 7 容器栈，`pnpm test:e2e` 终态 99 tests / 99 passed / 0 skipped / 0 failed（exit 0）；
      日志 `067-e2e-full3.log`（对照 `067-e2e-full.log` 凭据缺失 fail-closed skip 轮）。
- [x] Runbook/追踪矩阵（page-catalog PAGE-AUTH-002 口径登记由批准人裁定后同步）
      —— 本卡已更新 `work-breakdown.md` 状态行；Runbook 无需变更（`contractConsistency.runbookMatched: null`）。
      `page-catalog.md` PAGE-AUTH-002「展示 username」口径登记**超本卡 allowedPaths（不在 14 路径内）**，
      依 §2/§9 移交批准人裁定后同步，已在 EVD `coverage.notProven`/`limitations` 如实登记该移交项，非本卡擅自改动。

勾选必须附 Evidence Manifest 条目；提交代码本身不能作为勾选依据。全部勾选对应
`docs/ai-spec/tasks/evidence/EVD-P0BR-067-001.yaml`（status: PASS，evidenceLevel: E4，review.accepted: false）。

## 8. 验证命令

```text
backend/            ./mvnw.cmd -q verify                     exit 0   AC-P0B-UI-003（契约测试子集）
frontend/           pnpm lint; pnpm typecheck; pnpm test; pnpm build   各 exit 0   AC-P0B-UI-003
frontend/           pnpm test:e2e（真实 Compose 栈）          exit 0   AC-P0B-UI-002/003；fixme 解除后 98→98 全绿口径
contracts/          npx @redocly/cli lint + diff --fail-on-breaking（CI 等价 Node）  exit 0；本机不可用时按 §6.8 记 notProven
治理                三条 validate-*（Front Matter 所列）      exit 0
```

SKIP 判定：任何因 Docker/Node 缺失导致的跳过视为 notProven，不构成 PASS。

## 9. 停止条件

- 批准人裁定 §2「设计口径缺口」应走改标签路径 → 停止实施并回报（本卡作废重立）；
- redocly 在任何可用环境均无法给出结论且无 CI 执行记录 → 契约项 notProven，交接 IMPLEMENTED_UNVERIFIED；
- 发现 `username` 出现在任何日志、审计载荷或错误消息中 → 立即停止（§5 SECRETS）；
- 工作树存在与本卡 allowedPaths 重叠且来源不明的变更。

## 10. 完成报告

```text
Completed:
D-02 契约先行闭环：OpenAPI CurrentPrincipal 新增可选 username（string/nullable/maxLength 64，不进 required）；
后端 CurrentPrincipalView + AuthenticationApplicationService（USER 填充 / AGENT、CLIENT null）+
IdentityResponses.CurrentPrincipalPayload.from 透传；前端 auth.ts 类型同步、ProfilePage 渲染
username ?? subject 回退；profile.spec.ts 的 5ccbf47 test.fixme 解除为正式断言，并新增
AC-P0B-UI-002 最小权限主体（仅 notification:read）登录名回显 + /assets 越权 403 用例。
交接状态 IMPLEMENTED_UNVERIFIED（AI 不自批，DEC-009）：E4 证据链完整但 review.accepted=false、
代码未提交为干净 Commit，待验证权威放行。

Changed files:
contracts/openapi/aihub-v1.yaml
backend/src/main/java/com/aihub/identity/application/CurrentPrincipalView.java
backend/src/main/java/com/aihub/identity/application/AuthenticationApplicationService.java
backend/src/main/java/com/aihub/identity/api/IdentityResponses.java
backend/src/test/java/com/aihub/identity/api/AuthControllerTest.java
frontend/src/shared/types/auth.ts
frontend/src/features/auth/ProfilePage.tsx
frontend/src/features/auth/ProfilePage.test.tsx（allowedPaths 实施期补正，用户已批准）
frontend/e2e/profile.spec.ts
docs/ai-spec/tasks/TASK-P0BR-067.md、docs/ai-spec/tasks/evidence/EVD-P0BR-067-001.yaml、
docs/ai-spec/02-delivery/work-breakdown.md（治理收尾文档）

Requirements and scenarios satisfied:
REQ-IAM-002 / REQ-UI-001；AC-P0B-UI-003（USER 回显 E4 + AGENT null 契约测试 E2 + 缺失回退 E2 +
匿名 401 不回归 E2/E4 + 契约非破坏性 E4-lint）；AC-P0B-UI-002（最小权限主体仍见本人登录名、
越权 403 不回归 E4）。均登记于 EVD coverage.proven；coverage.notProven 保留 4 条（见下）。

Contract/database changes:
契约：CurrentPrincipal.username 可选字段（redocly lint PASS；oasdiff breaking =
「No breaking changes to report」）。数据库：无（username 已在 V1 local_user，无 Migration）。

Validation run and results:
validate-spec PASS（148 docs / 727 IDs）；validate-task-card 067 PASS；-CheckChangedPaths PASS
（14 allowedPaths + 3 preExistingDirtyPaths）；backend mvnw -q verify exit 0（757 tests /
0F / 0E / 10 skipped，skip 均为既存性能测试/UploadTransferIT 环境条件跳过）；frontend
lint / typecheck exit 0（profile.spec.ts 终稿后复跑）；Vitest 串行 49 files / 218 tests 全过；
pnpm build exit 0；pnpm test:e2e 真实 7 容器栈终态 99 tests / 99 passed / 0 skipped / 0 failed
exit 0（2.9m，凭据自 gitignored deploy/compose/.env 注入）；redocly lint + oasdiff 如上。
日志归档 build-logs/067-*.log 与 EVD-P0BR-067-*.log，清单见 EVD artifacts.logs。

Evidence artifacts:
docs/ai-spec/tasks/evidence/EVD-P0BR-067-001.yaml（status PASS，evidenceLevel E4，
review.accepted false）+ build-logs 归档（067-e2e-full3.log 为 E4 终态主证）。

Validation not run and reasons:
1) 卡 §8/requiredValidationCommands 的 redocly diff --fail-on-breaking：@redocly/cli 上游
已移除 diff 子命令（2.55.0/1.34.1/1.29.0/1.25.0 均无），CI 等价环境同样不可执行；以
§6.8 oasdiff 替代取证，CI 步骤缺陷如实申报（不在 allowedPaths，不改）。
2) AGENT /me 的 E3（真实依赖集成层）：卡 §4 表与 scenarioEvidencePlan 口径不一致，
按 EvidencePlan 的「后端契约测试」E2 取证，差异登记 EVD limitations 提请裁定。
3) 卡 §6.2 /me 响应示例：契约本无 operation 级 example 块，不为单字段发明示例结构，
登记 notProven。

Remaining risks or follow-ups:
CI ci.yml L147-162 breaking-diff 步骤会在 PR-to-main 时非零退出（既存缺陷，建议治理卡换用
oasdiff）；page-catalog.md PAGE-AUTH-002「展示 username」口径登记超本卡 allowedPaths，
移交批准人裁定（§2/§9）；本卡全部改动未提交，提交与 review.accepted 置位由验证权威执行；
5173 Vite dev server 为工作树外长服务（E4 入口既有口径）。
```
