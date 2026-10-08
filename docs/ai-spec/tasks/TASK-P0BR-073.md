---
schemaVersion: harnessdg.task/v1
taskId: TASK-P0BR-073
status: READY
implementationAuthorized: true
phase: P0-B
baseCommit: 7e53aacf9c9eba11a7f2365c9507ff69b735feaa
stageGatePassed: true
stageGateEvidence: 既存基础设施缺陷的正式收口卡。差距事实链：
  TASK-P0BR-070 §8 follow-ups ① 登记「frontend 容器 nginx `location /assets/`
  前缀遮蔽 6 条 `/assets/:assetId...` SPA 深链的整页加载（真实用户缺陷，
  非仅测试障碍）」；EVD-P0BR-050-001 limitations 首次定性该遮蔽，
  仓内 frontend/e2e/support.ts L63-65、dataset.spec.ts L97、profile.spec.ts L141、
  asset.spec.ts L176 四处注释长期以「客户端导航绕行」回避整页加载；
  2026-09-30 curl 实证 8080 栈 `/assets/deadbeef/settings` → 404、
  `/dashboard` → 200。批准人 User 于 2026-09-30 授权起草本卡，并裁定修复路线为
  方案 B（构建产物目录改名 static，与 SPA 业务路由彻底分名）与三项 E4 验收面
  （6 条深链整页加载用例 / 解除三处 e2e 绕行注释 / e2e 基线改以 8080 为权威）。
  实施批准与 approvedBy/approvedAt/status 回写由批准人在本卡 DRAFT 审阅后另行处置
  （DEC-009：AI 不得在同一实施会话自行填写 approvedBy/approvedAt 或将 DRAFT 置 READY）。
  批准人 User 于 2026-10-08 在 DRAFT 卡经人工审阅后明确批准实施，
  approvedBy/approvedAt/status 由 AI 如实登记批准结果（非自批，与 071/072 同惯例）。
approvedBy: User
approvedAt: 2026-10-08T00:55:19Z
stageGateEvidenceRefs:
  - docs/ai-spec/tasks/evidence/EVD-P0BR-070-001.yaml
  - docs/ai-spec/tasks/evidence/EVD-P0BR-050-001.yaml
requirements:
  - REQ-UI-001
acceptanceScenarios:
  - AC-P0B-UI-001
  - AC-P0B-UI-006
decisions:
  - DEC-009
scenarioEvidencePlan:
  - AC-P0B-UI-006|E4|真实 Compose 8080 栈新增 spa-deep-link.spec.ts：六条
    `/assets/...` 深链（详情/settings/lineage/access/versions/版本详情）
    page.goto 整页加载均返回 index.html 并渲染 SPA 真实页面（非 404、
    非 nginx 原生错误页）；curl 双口径取证：构建产物 JS 从 `/static/` 前缀
    返回 200 + `Cache-Control: public, max-age=604800, immutable`（缓存能力
    不回归），`/assets/` 前缀不再存在静态遮蔽 location
  - AC-P0B-UI-001|E4|深链整页加载走真实会话恢复路径（access Token 仅内存、
    刷新后由 refresh Cookie 引导回原路由）：解除 support.ts/dataset.spec/
    profile.spec/asset.spec 四处绕行注释改为真实 goto/reload；playwright
    默认 baseURL 改 8080 为权威口径后全量 e2e（112 既有基线 + 本卡新增用例）
    0 failed / 0 skipped
crossCuttingPlan:
  - AUTHN|深链整页加载依赖既有 refresh-Cookie 会话恢复实现（AC-P0B-UI-001 现状）；
    本卡不改认证链，只是首次让 e2e 真实走该路径；e2e 用例必须先登录再 goto 深链
  - AUTHZ|不改任何权限判定；routes.tsx guarded 六条路由的权限门与页面渲染零变更
  - DB_FILTER|N/A - 不改列表查询与权限下推 SQL
  - STATE|N/A - 不改状态机
  - IDEMPOTENCY|N/A - 无后端交互变更
  - CONSISTENCY|前后端与部署面一致性：vite `build.assetsDir: 'static'` 与
    frontend 容器 nginx `location /static/` 必须同卡同变更（改名不同步=全站白屏）；
    外层 deploy/compose/nginx/nginx.conf 为纯反代全量转发，天然兼容，不改
  - ERRORS|不新增 ErrorCode；nginx 原生 404 遮蔽缺陷消除
  - AUDIT|N/A - 纯前端静态服务与测试口径变更，不触达审计路径
  - NOTIFICATION|N/A - 不触碰通知面
  - TAXONOMY_I18N|N/A - 不新增/修改任何 i18n 键
  - CONFIG|frontend/vite.config.ts 与 frontend/nginx/default.conf 属前端构建/服务
    配置，非 deploy/ 运行时配置；deploy/ 属 forbiddenPaths（脚本仅运行取证不改动）
  - OBSERVABILITY|N/A - 不新增指标/告警
  - SECRETS|无 Token/凭据落盘；build-logs 工件统一 *.log 扩展名不入库
allowedPaths:
  - frontend/vite.config.ts
  - frontend/nginx/default.conf
  - frontend/playwright.config.ts
  - frontend/e2e/support.ts
  - frontend/e2e/asset.spec.ts
  - frontend/e2e/dataset.spec.ts
  - frontend/e2e/profile.spec.ts
  - frontend/e2e/spa-deep-link.spec.ts
  - docs/ai-spec/02-delivery/work-breakdown.md
  - docs/ai-spec/tasks/TASK-P0BR-073.md
  - docs/ai-spec/tasks/evidence/EVD-P0BR-073-001.yaml
preExistingDirtyPaths:
  - build-logs/verify-bom.ps1|b5003338d283e84b4b6dabaf0d9e47b6d90fe18b780d0fd2e59851d467ac9b3d
forbiddenPaths:
  - frontend/src
  - backend
  - contracts
  - deploy
  - worker
  - V1__baseline.sql
  - V2__asset_catalog.sql
  - docs/ai-spec/tasks/TASK-P0BR-070.md
  - docs/ai-spec/tasks/evidence/EVD-P0BR-070-001.yaml
requiredEvidenceLevel: E4
evidenceTemplate: docs/ai-spec/templates/evidence-manifest.yaml
evidenceManifests:
  - docs/ai-spec/tasks/evidence/EVD-P0BR-073-001.yaml
requiredValidationCommands:
  - ./docs/ai-spec/tools/validate-spec.ps1
  - ./docs/ai-spec/tools/validate-task-card.ps1 -TaskPath docs/ai-spec/tasks/TASK-P0BR-073.md
  - ./docs/ai-spec/tools/validate-task-card.ps1 -TaskPath docs/ai-spec/tasks/TASK-P0BR-073.md -CheckChangedPaths
  - pnpm lint（frontend 目录）
  - pnpm typecheck（frontend 目录）
  - pnpm build（frontend 目录；产物 dist/static/ 出现且 dist/assets/ 不存在）
  - docker compose build frontend + docker compose up -d frontend（deploy/compose 目录；重建镜像，health 就绪）
  - curl -sI http://localhost:8080/static/（构建产物前缀 200 + immutable 取证）
  - curl -s -o NUL -w '%{http_code}' http://localhost:8080/assets/deadbeef/settings（深链 200 取证；实施时以 dist 内真实 JS 文件名补一条 /static/*.js 取证，一并写入 EVD）
  - pnpm test:e2e（frontend 目录；默认 8080 权威口径全量，0 failed / 0 skipped；skip 视为 notProven）
---

# TASK-P0BR-073：前端静态目录改名 static，解除 nginx 对 SPA 深链的遮蔽（方案 B）

> 状态：`READY`（批准人 User 于 2026-10-08 批准实施，`implementationAuthorized: true`；
> DEC-009——批准权在批准人，AI 如实登记）。
> 规则：一个任务只交付一个可独立验收的纵向结果——本卡纵向结果 =
> 「8080 真实栈上六条 `/assets/...` SPA 深链整页加载可用，构建产物静态缓存
> 能力迁移至 `/static/` 前缀不回归，e2e 绕行注释清零且基线以 8080 为权威」。
> 门禁：YAML Front Matter 是机器权威；正文不得与其冲突。

## 1. 目标

frontend 容器 nginx 的 `location /assets/`（静态资源缓存规则）与 SPA 业务路由
`/assets/:assetId...`（`frontend/src/app/router/routes.tsx` L55-63 六条）同前缀冲突：
前缀 location 命中即按文件系统查找，**永不回退** `location /` 的
`try_files $uri $uri/ /index.html`，导致六条资产域深链的整页加载（刷新、收藏、
分享、新标签打开）全部返回 nginx 原生 404。这是**真实用户缺陷**，不只是测试障碍——
EVD-P0BR-050-001 定性后，仓内四处 e2e 注释以「客户端导航绕行」长期回避该路径。

本卡按批准人裁定的**方案 B** 修复：vite 构建产物目录改名 `static`
（`build.assetsDir: 'static'`），nginx 缓存 location 同步改 `location /static/`，
构建产物与 SPA 业务路由**彻底分名**，零 nginx 特殊逻辑；`frontend/src` 六条路由
字符串零改动。同步落地三项 E4 验收面：六条深链整页加载用例、解除四处绕行注释、
e2e 基线改以 8080 为权威口径。

## 2. 需求与追踪

- 需求：`REQ-UI-001`（公共管理端页面端到端可用——资产域六条路由的整页直达是
  基本可用形态）。
- 验收：`AC-P0B-UI-001`（访问受保护路由 → 登录/会话恢复后返回**原路由**——该
  场景前提是深链能整页加载；现状 8080 下该场景对资产域六条路由物理不可达）、
  `AC-P0B-UI-006`（关键路由均有自动化证据——本卡补齐深链整页加载用例族并让
  既有绕行用例改走真实路径）。
- 上游证据：`EVD-P0BR-070-001.yaml`（§8 follow-ups ① 登记）；
  `EVD-P0BR-050-001.yaml` limitations（遮蔽首次定性）。

## 3. 取证事实（2026-09-30，HEAD `7e53aac`）

### 缺陷本体

- `frontend/nginx/default.conf` L14-17：
  `location /assets/ { expires 7d; add_header Cache-Control "public, max-age=604800, immutable"; }`
  ——前缀 location 无 try_files，遮蔽同前缀 SPA 深链；L18-20 `location /` 的
  `try_files $uri $uri/ /index.html` 仅在**未命中更长前缀**时生效。
- curl 实证（8080 真实栈）：`/assets/deadbeef/settings` → **404**；
  `/dashboard` → 200。受损路由恰为 routes.tsx L57-63 六条：
  `assets/:assetId`、`assets/:assetId/settings`、`assets/:assetId/lineage`、
  `assets/:assetId/access`、`assets/:assetId/versions`、
  `assets/:assetId/versions/:versionId`。
- 5173 Vite dev server 自带 history fallback 不受影响——历史 e2e 基线一直在
  5173 取证，故缺陷从未在测试面暴露。

### 修复面事实

- `frontend/vite.config.ts`：`build` 段无 `assetsDir`（默认 `assets`）——新增 `assetsDir: 'static'` 即改名
  产物目录；index.html 无静态资源硬编码（仅 `/vite.svg` 与 `/src/main.tsx`，
  构建注入的引用随 assetsDir 自动变为 `/static/...`）。
- `frontend/Dockerfile` runtime 阶段 COPY `nginx/default.conf` 与 `dist/`——修复需重建 frontend 镜像。
- 外层 `deploy/compose/nginx/nginx.conf` 纯反代：`location / { proxy_pass http://$aihub_frontend; }` 全量转发，无路径
  遮蔽，**不改**（deploy/ 属 forbiddenPaths）。
- API 路径不受影响：`apiClient` 带 `/api` baseURL，仓内 80 处 `'/assets/...'`
  引用为 API 调用（实际发出 `/api/v1/assets/...`）或 SPA 客户端路由字符串，
  均与构建产物目录名无关。
- deploy/compose/scripts 全量 grep 无 `/assets/` 静态路径依赖（命中均为
  `/api/v1/assets/...`），verify.sh 校验面不受改名影响。

### e2e 绕行注释（四处，本卡清零）

- `frontend/e2e/support.ts` L60-70：`openAssetDetailViaCatalog` 头注明示
  「刻意不使用 page.goto('/assets/...')…（详见 EVD-P0BR-050-001 的 limitations）」。
- `frontend/e2e/dataset.spec.ts` L97、`frontend/e2e/profile.spec.ts` L141：同型绕行注释。
- `frontend/e2e/asset.spec.ts` L176：「深链本身被 nginx 遮蔽，不能整页加载」——
  L167-186「缺失资产」用例中 `'/assets/0000...as'` 为 directApi 调用（走 /api/，
  不受影响），但注释叙述与 route-mock 结构记录了遮蔽前提，修复后同步收紧为
  真实深链整页加载 + 空态断言。
- `frontend/e2e/settings-form.spec.ts` L312 `page.reload()` 即 TASK-P0BR-070
  8080 轮唯一失败项（切片⑧）——reload 落到被遮蔽的 `/assets/...` URL 返回 404。
  修复后该 reload 走真实路径，无需改该 spec（若复跑仍红则按事实另卡处置）。

### 测试口径事实

- `frontend/playwright.config.ts` L20：`baseURL: process.env.E2E_BASE_URL ?? 'http://localhost:5173'`——默认
  5173 是缺陷长期隐身的原因之一。批准人裁定：**8080 为权威口径**（默认值改
  `http://localhost:8080`，`E2E_BASE_URL` 环境变量保留为开发逃生门）。

## 4. 修复方案

1. **构建产物改名**（`frontend/vite.config.ts`）：`build` 段新增
   `assetsDir: 'static'`。验收物证：`pnpm build` 后 `dist/static/` 存在、
   `dist/assets/` 不存在。
2. **nginx 缓存规则同步改名**（`frontend/nginx/default.conf`）：
   `location /assets/` → `location /static/`，`expires 7d` 与
   `Cache-Control "public, max-age=604800, immutable"` 原样保留。步骤 1/2
   **必须同卡同一提交**（改名不同步 = 全站白屏）。
3. **e2e 绕行注释清零**：`support.ts` `openAssetDetailViaCatalog` 改为真实
   `page.goto('/assets/' + assetId)` 形态（登录先行、refresh-Cookie 会话恢复），
   四处注释删除遮蔽叙述并改写为「深链整页加载已由 TASK-P0BR-073 修复」；
   `dataset.spec.ts`/`profile.spec.ts` 绕行段改真实 goto/reload；
   `asset.spec.ts` L167-186 用例注入 404 的方式改为真实深链整页加载 +
   route-mock 仅 mock API（不再回避页面加载）。**不放宽任何既有断言**——
   全部为「绕行 → 真实路径」的收紧。
4. **新增深链用例族**（`frontend/e2e/spa-deep-link.spec.ts`）：六条路由各一条
   `page.goto` 整页加载用例（登录后直达：详情/settings/lineage/access/
   versions/版本详情），断言渲染出目标页面真实内容特征（非 404、非
   「页面不存在」兜底）；版本详情类无稳定夹具 ID 的路由按既有夹具资产动态取
   ID（复用 support.ts 登录与 API helper）。
5. **8080 权威口径**（`frontend/playwright.config.ts`）：默认 baseURL 改
   `http://localhost:8080`；文件头注释同步「运行前提：docker compose up（权威
   口径）；5173 为开发口径经 E2E_BASE_URL 覆盖」。
6. **镜像重建与取证**：`docker compose build frontend && up -d frontend` →
   curl 双口径（`/static/*.js` 200+immutable；六条深链 200 index.html）→
   `pnpm test:e2e` 全量 8080 通过（0 failed / 0 skipped，基线 = 070 的 112 +
   本卡新增 6）。
7. **治理文档**：`work-breakdown.md` 073 行（状态按实施终态）；
   `EVD-P0BR-073-001.yaml`（注意被机器判定的标量行——result/exitCode/
   skippedTests——行尾不得附注释，070 收口已实证校验器正则不剥离行尾注释）。

## 5. 垂直切片与验收

| # | 切片 | 证据 |
|---|------|------|
| 1 | vite assetsDir=static + nginx location /static/（同变更） | pnpm build 产物目录 + diff |
| 2 | 镜像重建 + curl 双口径（static 200+immutable / 深链 200） | build-logs curl 记录 → EVD |
| 3 | 新增 spa-deep-link.spec.ts 六条深链整页加载 | 8080 全量 e2e 新用例 PASS |
| 4 | 四处绕行注释清零改真实 goto/reload | 8080 全量 e2e 既有用例不回归 |
| 5 | playwright 默认 8080 权威口径 | 全量 e2e 运行日志 URL 证据 |
| 6 | work-breakdown + EVD + 本卡 §6/§8 | 治理三连 PASS |

## 6. 门禁清单（实施收口时逐项勾选）

- [x] validate-spec / validate-task-card（含 -CheckChangedPaths）PASS
- [x] frontend `pnpm lint / typecheck / build` 全 PASS；`dist/static/` 存在且
      `dist/assets/` 不存在
- [x] 真实 Compose 栈：frontend 镜像重建 health 就绪；curl 双口径取证
      （`/static/*.js` 200+immutable、六条深链 200 index.html）
- [x] `pnpm test:e2e` 默认 8080 口径全量：0 failed / 0 skipped
      （基线 112 + 新增 6 = 118，如实记录终值）
- [x] EVD-P0BR-073-001.yaml 完成且 secrets 脱敏确认（redactionChecked: true）
- [x] work-breakdown 073 行登记；四处绕行注释在 diff 中清零

## 7. 越界与禁止事项

- **不改 frontend/src**：六条 `/assets/:assetId` 路由字符串、页面组件、守卫逻辑
  零改动——方案 B 的本质是**构建产物与业务路由分名**，而非改路由。任何「顺手」
  把路由改名（如 `/asset-detail/`）的提议均违反本卡裁定。
- **不改 deploy/**：外层 nginx 纯反代天然兼容；verify.sh 等脚本仅运行取证不改。
- **不改 backend / contracts / worker / V1/V2**：纯前端静态服务与测试口径变更。
- **不采用方案 A**（给 `location /assets/` 加 try_files 回退）：回退的 index.html
  会继承该 location 的 `immutable` 缓存头，发版有卡旧页风险；且 SPA 路由与静态
  前缀语义继续混叠。方案 B 已由批准人裁定。
- **步骤 1/2 不得拆分提交**：改名不同步即全站资源 404。
- **不放宽任何 e2e 断言**：绕行改真实路径是收紧；若既有用例在真实路径下暴露
  新缺陷，如实登记 follow-up，不在本卡内静默修复（frontend/src 禁改面）。
- 本卡不追改 070 已提交工件（TASK-P0BR-070.md 与 EVD-P0BR-070-001.yaml 均在
  forbiddenPaths）。

## 8. 完成报告

**终态（2026-10-08）**：方案 B 修复落地并经 E4 全量实证。8080 真实栈 Playwright
**118 passed / 0 failed / 0 skipped**（exit 0，2.9m = 072 基线 112 + 本卡新增 6）；
070 遗留唯一失败 settings-form⑧ reload 转绿。详见 `EVD-P0BR-073-001`。

按 §4 七步逐项：

1. vite `build.assetsDir: 'static'`——`pnpm build` 物证 `dist/static/` 存在、
   `dist/assets/` 不存在（073-build.log）。
2. nginx `location /static/`（7d + immutable 原样保留）——与步骤 1 同一工作树变更、
   待同一提交。
3. 四处绕行注释清零（git diff 删除行可证）：`support.ts` 两处注释改写、
   `dataset.spec` 用例②与 `asset.spec` 缺失资产用例改真实 `page.goto`
   （route-mock 仅拦 API）、`profile.spec` 改 `login()` + `goto('/assets')`。
   **未放宽任何既有断言**；反而暴露并修复了 profile.spec 自身时序缺陷（内联 click
   未等登录落地即整页加载 → refresh Cookie 未落盘弹回 /login；首跑 117/1 → 修复后
   全量 118/0，属收紧路径上的真实发现，非产品缺陷）。
4. `spa-deep-link.spec.ts` 六条深链整页加载用例全绿（详情/settings/lineage/
   access/versions/版本详情；断言真实 i18n 文案特征 + 「页面不存在」兜底 count=0）。
5. `playwright.config.ts` 默认 baseURL 改 `http://localhost:8080`，5173 降为
   `E2E_BASE_URL` 开发口径。
6. 镜像重建 Up；curl 双口径：`/static/index-BwcVgUTL.js` → 200 +
   `public, max-age=604800, immutable`；六条深链 + /dashboard → 200 text/html；
   旧路径 `/assets/index-*.js` → 200 回退 SPA（遮蔽 location 已不存在的期望行为）。
7. EVD + work-breakdown + 本节 + 治理四连（见 EVD governance 段）。

**登记偏差**（均已写入 EVD coverage.notProven / 说明，非静默处理）：

- §4 步骤 3 字面要求 `openAssetDetailViaCatalog` 本体改真实 goto；实际保留其目录
  点击实现（helper 被 7+ 处用例依赖，签名与行为稳定性优先），深链证据由新用例族 +
  三处调用方真实化承担，helper 仅清遮蔽叙述注释。权衡已在 EVD notProven[0] 登记。
- curl 第 5 口径（旧 `/assets/index-*.js` 路径）返回 200 而非 404：分名后无任何
  静态遮蔽的期望终态，已在 EVD environmentDifferences 定性。
- 收口轮 `git status` 出现三个 backend 文件 stat-dirty 假脏（diff 内容 0 行，
  工具链 touch mtime 所致），checkout 刷新索引后消除；本卡全程未写 backend，
  forbiddenPaths 未触达。

**follow-ups（不属本卡范围）**：仓内五处历史「遮蔽」叙述文字勘误（admin-states.spec
L147、lineage.spec L116、error-paths.spec L11-12、asset.spec L84、ci.yml L306——
前四者不在 allowedPaths、ci.yml 属 forbiddenPaths）；`review.accepted` 与提交由
验证权威处置（DEC-009：本卡 IMPLEMENTED_UNVERIFIED，未提交、未自批）。

Completed: 方案 B 全链路（构建/服务/测试口径/用例/治理）落地，E4 终态 118/0/0
Changed files: frontend/vite.config.ts, frontend/nginx/default.conf,
  frontend/playwright.config.ts, frontend/e2e/{support,asset,dataset,profile,spa-deep-link}.spec 系 5 文件,
  docs/ai-spec/02-delivery/work-breakdown.md, docs/ai-spec/tasks/TASK-P0BR-073.md,
  docs/ai-spec/tasks/evidence/EVD-P0BR-073-001.yaml
Contract/database changes: 无（纯前端静态服务与测试口径；frontend/src、backend、
  deploy、contracts 零改动）
Validation run and results: validate-spec PASS；validate-task-card PASS；
  -CheckChangedPaths PASS；-CheckCompletion EXPECTED_FAIL（交接态设计内）；
  lint/typecheck/build exit 0；compose 镜像重建 Up；curl 双口径 PASS；
  playwright 8080 全量 118 passed / 0 failed / 0 skipped
Validation not run and reasons: pnpm test（Vitest）未跑——本卡零 frontend/src 变更、
  无组件逻辑改动，单测面无暴露面（E4 全量 e2e 为超集口径）；verify.sh 未跑——
  deploy/ 零改动
Remaining risks or follow-ups: 五处历史遮蔽叙述文字勘误另卡；旧 /assets/*.js 引用
  回退 index.html 不命中缓存（分名既定取舍）；提交与 review.accepted 待验证权威
