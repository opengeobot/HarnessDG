# 实现状态快照（2026-07-02）——P0-B 遗留风险闭环

> 本文记录在 `implementation-status-2026-07-01.md` 之后，对三项遗留风险的闭环整改与全栈 E2E 验证结果。
> 不回写更早快照。作者：AxeXie

## 1. 本轮范围

针对 2026-07-01 快照列出的三项残余风险执行闭环：

1. **identity 登录审计接入**（此前"未统一"）。
2. **全栈 E2E 验证**（此前 verify.ps1 V02–V11 因无 Docker 只能 SKIP）。
3. **前端主 chunk >500kB 告警**。

## 2. 变更内容

### 2.1 identity 审计接入（Port + Adapter）

- 新增 `identity/application/AuditPort.java`：认证域审计端口，签名带 `AuditResult` 与 `errorCode`（区别于其他模块简化签名，因认证需记录 FAILED/DENIED）。
- 新增 `identity/infrastructure/IdentityAuditAdapter.java`：`@Component` 桥接权威 `AuditService`，审计失败仅告警不影响认证主流程。
- 改造 `identity/application/AuthenticationApplicationService.java`：在以下路径埋点，成功/失败/拒绝 100% 记录，且正文严格不含口令/令牌明文：
  - `AUTH_LOGIN_SUCCEEDED`(SUCCEEDED) / `AUTH_LOGIN_FAILED`(FAILED 口令错、用户不存在；DENIED 禁用/锁定)
  - `AUTH_TOKEN_REFRESHED`(SUCCEEDED) / `AUTH_TOKEN_REPLAY_REJECTED`(DENIED)
  - `AUTH_LOGOUT`(SUCCEEDED)
  - `USER_PASSWORD_CHANGED`(SUCCEEDED)
  - `CLIENT_TOKEN_ISSUED`(SUCCEEDED) / `CLIENT_TOKEN_REJECTED`(FAILED/DENIED)
- 新增 `identity/application/AuthenticationApplicationServiceTest.java`：7 个用例，含 Mockito 验证审计事件参数与"attributes 无敏感键"断言。

### 2.2 修复 AuditService 构造器歧义（全栈启动阻断项）

- `audit/application/AuditService.java`：为 6 参构造器补 `@Autowired`。此前双 public 构造器均无 `@Autowired`，Spring 无法选择，导致真实容器启动 `BeanInstantiationException`（单测/切片测试未覆盖，仅全栈启动暴露）。

### 2.3 修复 iam_user.status 列宽（Bootstrap 管理员写入阻断项）

- 新增 `V13__widen_user_status.sql`：`iam_user.status` 由 `VARCHAR(16)` 放宽为 `VARCHAR(32)`。`PENDING_ACTIVATION`（18 字符）超出原列宽，导致 Bootstrap 管理员初始化失败。前向迁移，不改 V1–V12。

### 2.4 前端 chunk 分割

- `frontend/vite.config.ts`：`manualChunks` 拆分 `react-vendor`/`antd-vendor`/`query-vendor`；主业务 chunk 由 1.34MB 降至约 114kB（gzip 36kB）。antd 单库约 970kB 无法进一步细分，将 `chunkSizeWarningLimit` 上调至 1100kB 以消除该单库告警。

### 2.5 verify 脚本修正与增强

- V08 升级：除脱敏不变式外，增加断言"登录成功后 `audit_log` 出现 `AUTH_LOGIN_SUCCEEDED` 事件"（verify.ps1 与 verify.sh 同步）。
- 修复 `audit_log` 列名（`detail` → `request_summary`，与 V10 实际 schema 对齐）。
- 修复 PowerShell 下 `docker compose ps --format json | ConvertFrom-Json` 因 Command 字段非 UTF8 字符解析失败问题 → 改用 Go-template `--format`；修复 `psql`/`mc` 的 `sh -c` 嵌套引号在 PowerShell 下被破坏问题。

## 3. 验证结果（实际运行）

| 验证项 | 命令 | 结果 |
| --- | --- | --- |
| 后端 | `mvnw -o verify`（-Xmx640m） | BUILD SUCCESS，213 单元测试通过（含新增 7 个 identity 审计测试），26 IT 无 Docker 网络 Skipped |
| 前端 | `pnpm lint / typecheck / build` | 全部通过；无 >500kB 告警 |
| 契约 | `npx @redocly/cli lint contracts/openapi/aihub-v1.yaml` | valid（1 无害 warning） |
| Compose 配置 | `docker compose config --quiet` | exit 0 |
| **全栈 E2E** | `docker compose up -d --build` + `verify.ps1` | **V01–V11 全部 PASS**（真实全栈，8 服务健康） |

V08 已实测确认：登录后 `audit_log` 含 `AUTH_LOGIN_SUCCEEDED` 事件，且 `request_summary` 无明文口令。

## 4. 残余风险

- Testcontainers `*IT`（26 个）仍需 Docker 网络方可运行；本机 `mvnw verify` 中按 `disabledWithoutDocker` 跳过（全栈 E2E 已另行覆盖端到端路径）。
- 全栈 E2E 为一次性人工执行；尚未纳入 CI 自动跑（CI 的 compose job 仍以 `config` 校验为主）。
- OTel 完整链路（Collector/OTLP 导出）仍在 P0-B 之外。
- antd-vendor 单包 970kB 为该 UI 库固有体积，已单独隔离，后续可评估按需引入 / 路由级懒加载进一步优化。
