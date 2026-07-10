# Compose 部署与验证 Runbook

本文档说明如何在本地通过 Docker Compose 启动 AIHub 平台基线环境并执行验收。Compose 仅用于本地开发、CI 功能验证与单机 PoC，不提供高可用，生产重要数据不得只保存在单机 Volume 中。脚本现覆盖 P0-A（V01-V03）与 P0-B 公共底座冒烟（V04-V11）；未启动全栈时相关用例只标 SKIP，SKIP 不等于 PASS，不能把部分通过解释为整个 P0 完成。

## 目录

```text
deploy/compose/
├─ compose.yaml                       # 八服务编排
├─ .env.example                       # 环境变量示例（复制为 .env）
├─ nginx/nginx.conf                   # 单域名反向代理路由
├─ postgres/init/01-create-gitea-db.sql
├─ minio/init.sh                      # 创建 4 个 Bucket 与最小权限账号
├─ scripts/bootstrap.ps1              # 引导（准备 .env、构建、启动）
├─ scripts/verify.ps1                # 验收（V01-V11）
├─ scripts/verify.sh                  # 同上，Shell 等价
└─ fixtures/model/                    # 验证用最小资产 fixture
```

## 服务与端口

| 服务 | 说明 | 暴露 |
| --- | --- | --- |
| postgres | 业务库 + Gitea 库 | 容器网络 |
| minio | 对象存储数据面 | 控制台 127.0.0.1:9001 |
| minio-init | 一次性初始化 Bucket 与账号 | 容器网络 |
| gitea | 代码托管 | SSH 127.0.0.1:2222；Web 仅经 Nginx http://localhost:8080/git/ |
| backend | REST/MCP 控制面 | 经 Nginx |
| worker | 可靠任务（含 DVC 运行时） | 容器网络 |
| frontend | React 静态资源 | 经 Nginx |
| nginx | 单域名网关 | 127.0.0.1:8080 |

Nginx 路由（见 `nginx/nginx.conf`）：`/` → 前端、`/api/` → 后端 REST、`/mcp` → 后端 MCP（关闭缓冲）、`/actuator/health` → 健康检查、`/git/` → Gitea。

## Gitea 访问与未来 SSO 免登（预留）

Gitea Web 仅通过 Nginx 子路径入口 `http://localhost:8080/git/` 访问，不再单独对外暴露 3000 端口。`GITEA__server__ROOT_URL`（由 `.env` 的 `GITEA_PUBLIC_URL` 提供）固定为该地址，确保页面静态资源与链接均带 `/git/` 前缀。

平台本地账号与 JWT 已在 P0-B 中实现（V3 identity + V13 状态放宽 + V20 权限 seed 对齐）。平台 JWT 不等于 OIDC Provider；"平台登录后免登跳转 Gitea"仍是独立预留能力，需另行 ADR 后选择：

- **方案 A**：引入标准 Authorization Server/OIDC Provider，Gitea 作为 OAuth2/OIDC Client；回调地址以 `http://localhost:8080/git/` 为基准。
- **方案 B**：在 Nginx `/git/` 前置 `auth_request` 校验平台会话，配合 Gitea `ENABLE_REVERSE_PROXY_AUTHENTICATION` 透传可信用户头实现免登。

上述方案均以现有 `/git/` 入口为基准，接入时该入口地址保持不变。

## 启动流程

PowerShell：

```powershell
cd deploy/compose
Copy-Item .env.example .env   # 按需修改占位密码
docker compose pull
docker compose build
docker compose config --quiet
docker compose up -d
./scripts/bootstrap.ps1
./scripts/verify.ps1
```

Bash：

```bash
cd deploy/compose
cp .env.example .env
docker compose build
docker compose config --quiet
docker compose up -d
./scripts/verify.sh
```

> `.env` 与 `.env.local` 已被 `.gitignore` 忽略，禁止提交。`GITEA_SERVICE_TOKEN` 由 bootstrap 后在 Gitea 中创建并写回，勿提交 Git。

## 当前 P0-A 验收范围

`verify.ps1` / `verify.sh` 当前覆盖：

- **V01** Compose 配置合法（`docker compose config --quiet`）
- **V02** 核心服务健康（postgres / minio / gitea / backend）
- **V03** 四个 Bucket 存在且非匿名（gitea-storage / dvc-cache / asset-staging / asset-preview）

P0-B 公共底座用例（V04-V11）已实现，覆盖以下能力：

- 本地账号登录、访问/刷新 JWT 轮换、登出、禁用用户和旧刷新令牌重放拒绝；
- 角色/Scope/ACL 越权拒绝及资产列表数据库权限下推；
- 字典、平台/组织标签、停用回显与自由标签拒绝；
- JSON 日志上下文、Secret/JWT 脱敏和必审计事件完整性；
- 持久化任务租约/重试/Dead、站内通知和签名 Webhook 故障恢复；
- Prometheus Target、Trace 贯通和受保护的系统诊断端点。

资产创建、DVC 往返、发布和 MCP 等后续业务用例分别由 P1-P4 在公共底座上补充。

## P5 运维与质量（指针）

P5 运维 Runbook、备份恢复与安全脱敏验证见:

- [ops.md](../runbooks/ops.md) — Dashboard、DEAD Job/Webhook 告警与故障演练
- [backup-restore.md](../runbooks/backup-restore.md) — PostgreSQL/Gitea/MinIO 备份恢复
- [security.md](../runbooks/security.md) — 脱敏金丝雀 (`deploy/security/run-redaction-canary.sh`)

P5 验收在 P0-B 脚本基础上补充对账 Worker 外部系统调用、下载统计 API（DEC-016）与 `reconciliation_checkpoint` 表（V28）。

## P0-B 验收范围（V04-V11）

自 P0-B 起，`verify.ps1` / `verify.sh` 在 V01-V03 之外新增公共底座冒烟用例。脚本以网关基址
`http://localhost:8080` 访问 REST，用例编号与覆盖点如下：

| 用例 | 覆盖 | 前置 |
| --- | --- | --- |
| **V04** | Flyway V1-V23 成功迁移、关键表（`iam_principal`/`iam_user`/`iam_role`/`iam_role_binding`/`iam_resource_acl`/`system_dict_item`/`system_tag`/`asset_tag`/`system_config`/`job_task`/`api_idempotency`/`audit_log`/`notification`/`team`/`team_member`/`discussion_thread`/`asset_version`/`publish_request`/`webhook_inbox`/`system_alert`）存在 | postgres 容器运行 |
| **V05** | 用登录 bootstrap 管理员签发 JWT、携带 access token 调 `/me` 返回 200、无 Token 调 `/system/users` 返回 401（fail-closed） | backend readiness 就绪、bootstrap 管理员已创建 |
| **V06** | `/system/audit-logs`、`/system/metrics/summary` 无 Token→401、越权 Token→403 | backend 就绪 |
| **V07** | `/system/dictionaries`、`/system/tags` 无 Token→401、越权 Token→403 | backend 就绪 |
| **V08** | `audit_log` 表存在且任何正文都不含明文口令（脱敏恒定不变式）；identity 登录审计已通过 `IdentityAuditAdapter` 统一接入，断言 `AUTH_LOGIN_SUCCEEDED` 事件存在 | postgres 就绪 |
| **V09** | `/system/jobs` 默认拒绝 | backend 就绪 |
| **V10** | `/system/notifications` 默认拒绝 | backend 就绪 |
| **V11** | `/actuator/health` 返回 200、`/system/dependencies`（需 `system:observe`）默认拒绝 | backend 就绪 |
| **V12** | `/system/alerts`（需 `system:observe`）返回告警列表；DEAD 任务/Outbox 积压/认证失败可触发告警 | backend 就绪 |

## AC ID 到 V 编号覆盖映射

> 每个验收场景必须显式绑定覆盖它的 V 编号，未声明的场景不视为覆盖。

| AC ID | V 编号 | 覆盖范围 | 证据级别 |
| --- | --- | --- | --- |
| AC-P0B-ENG-001 | V01, 后端/前端单测 | 编译/Lint/类型/单测/ArchUnit | E1/E2 |
| AC-P0B-ENG-002 | V01 | OpenAPI/MCP/Event lint | E1/E3 |
| AC-P0B-ENG-003 | V01 | breaking diff CI | E1 |
| AC-P0B-ENG-004 | V04 | Flyway V1-V23 迁移完整性 | E3 |
| AC-P0B-ENG-005 | V04 | Migration 升级不丢数据 | E3 |
| AC-P0B-ENG-006 | V04-V21 | 无空 Principal/allow-all/默认全 Scope | E1/E3 |
| AC-P0B-IAM-001 | V05 | Bootstrap 管理员签发 JWT | E4 |
| AC-P0B-IAM-002 | V05 | 临时密码登录 | E4 |
| AC-P0B-IAM-003 | V05 | 首登改密 | E4 |
| AC-P0B-IAM-004~005 | V05 | 登录失败/锁定 | E4 |
| AC-P0B-IAM-006~007 | V05 | 刷新轮换/重放 | E4 |
| AC-P0B-IAM-008 | V05 | 禁用用户实时失效 | E4 |
| AC-P0B-IAM-009 | V05 | 登出后 Token 吊销 | E4 |
| AC-P0B-IAM-010 | V05 | JWT 验证 fail-closed | E3/E4 |
| AC-P0B-IAM-011 | V05 | 并发刷新语义 | E4 |
| AC-P0B-IAM-012 | V05 | 用户 CRUD 生命周期 | E4 |
| AC-P0B-AGT-001~005 | V06, V07 | Agent 注册/凭据/Scope/Tool | E4 |
| AC-P0B-AUTH-001~009 | V06, V14 | 组织/项目/Team/角色/ACL | E4 |
| AC-P0B-TAX-001~006 | V07 | 字典/标签/i18n | E4 |
| AC-P0B-CFG-001~003 | V07 | 类型化配置/Secret/并发 | E4 |
| AC-P0B-IDM-001~003 | V13 | 幂等键重放 | E4 |
| AC-P0B-JOB-001~005 | V09 | 持久化任务 | E4 |
| AC-P0B-AUD-001~005 | V08 | 审计脱敏与完整性 | E4 |
| AC-P0B-NOT-001~005 | V10, V18 | 通知/Webhook | E4 |
| AC-P0B-OBS-001~005 | V11, V21 | 指标/Trace/健康/告警 | E4/E5 |
| AC-P0B-UI-001~006 | 前端测试/E2E | Auth/Route/i18n/表单/组件 | E2/E4 |

> V05 仅覆盖 IAM 场景的一小部分 E1/E3 冒烟；完整 E4 需 Compose 全栈。V06/V07/V09/V10/V11 的 401/403 仅覆盖拒绝分支的冒烟；完整 E4 需全栈浏览器测试。

### 前置：bootstrap 管理员凭据

V05-V08 需要一个可登录的管理员。Compose `backend` 服务通过 `.env` 的
`AIHUB_BOOTSTRAP_ADMIN_USERNAME` / `AIHUB_BOOTSTRAP_ADMIN_PASSWORD` 引导首个管理员
（`.env.example` 提供开发默认 `admin` / `change-me-admin-01`；生产须改用 Secret 文件并强制首登改密）。
脚本按 `.env` → `.env.example` → 内置默认的顺序读取该凭据。首登强制改密不影响 `/me`（改密门放行认证与 `/me`）。

由于 bootstrap 管理员默认仅持 `ADMIN_SCOPES`（user/authorization/agent），V06-V11 以
“无 Token→401、越权 Token→403”验证**默认拒绝**语义，而非以管理员令牌断言 200。完整“有权限 200”
路径由后端集成测试（如 `AuthorizationIT`）覆盖。

### 运行方式

完整验收需先启动全栈，再运行脚本：

```powershell
cd deploy/compose
Copy-Item .env.example .env
docker compose up -d          # 拉取镜像并构建 backend/frontend，首次较慢
./scripts/verify.ps1
```

```bash
cd deploy/compose
cp .env.example .env
docker compose up -d
./scripts/verify.sh
```

### Docker / 服务不可用时的行为

脚本对每个用例先探测依赖服务是否在运行：

- 未执行 `docker compose up` 时，V02-V11 会逐项输出 **SKIP** 并附原因（如“backend 不可达”“postgres 容器未运行”），
  **不会**误报为 PASS，也不会因缺少服务而 FAIL 崩溃。
- 只有真实断言失败（如迁移缺失、fail-closed 被绕过、审计缺失或明文口令泄漏）才记为 **FAIL** 并以非零码退出。
- 因此“仅 `docker compose config` 通过”与“全栈就绪后全部 PASS”是两种不同结果；SKIP 数量会在结尾汇总提示，
  提醒必须在服务就绪后补验，SKIP 绝不等同于通过。

## 健康检查分层

| 端点 | 用途 |
| --- | --- |
| `/actuator/health/liveness` | 容器是否需重启（仅 JVM 基本状态） |
| `/actuator/health/readiness` | 是否接收流量（含 PostgreSQL 探测） |

## 常见问题

- 后端 readiness 一直 DOWN：检查 PostgreSQL 是否 healthy、数据源 URL/账号是否匹配 `.env`。
- minio-init 失败：查看 `docker compose logs minio-init`，确认 Root 凭据与 mc 版本兼容。
- Gitea 无法启动：确认 `01-create-gitea-db.sql` 已在 postgres 首次初始化时执行（仅空数据卷首启执行）。
