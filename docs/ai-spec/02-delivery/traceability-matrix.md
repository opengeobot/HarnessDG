# 能力追踪矩阵（初始审计版）

> 状态：`DRAFT`
> 注意：本表记录当前证据判断，不是新的产品完成声明。

## 1. 状态说明

- `OPEN`：产品决策缺失；
- `PARTIAL`：存在实现片段，但需求闭环明显缺失；
- `IMPLEMENTED_UNVERIFIED`：实现较完整，仍缺规定等级的证据；
- `VERIFIED`：尚无任何能力在本初始审计中获得该状态。

## 2. P0-B 与冻结资产整改

| 能力 ID | 能力 | 关键规范 | 契约/数据 | 实现证据 | 缺失的决定性证据或规则 | 当前状态 |
| --- | --- | --- | --- | --- | --- | --- |
| `CAP-IAM-001` | 本地用户与 Principal | PRD 5.4、ADR-0002 | OpenAPI auth/users；V3/V13 | identity 代码、单测、IdentityIT、Compose 登录 | 完整锁定/禁用/首登改密 E4；契约状态仍 planned | `IMPLEMENTED_UNVERIFIED` |
| `CAP-IAM-002` | JWT 生命周期 | PRD 5.4、11.1 | `iam_token`；auth refresh/logout | JwtTokenService、IdentityIT | 轮换、旧 Token 重放、禁用即时失效和 Cookie 属性 E4 | `IMPLEMENTED_UNVERIFIED` |
| `CAP-IAM-003` | Agent/Service/API Client | PRD 5.4、9.2 | Agent API、`iam_agent_tool` | Agent 管理代码和页面 | 受控 Scope/Tool、禁用后已签发 Token 失效、MCP 双控 E4 | `PARTIAL` |
| `CAP-ORG-001` | 组织与项目 | PRD 5.5 | V5、organization API | 组织/项目代码和 IT | 多组织语义、授权成功/越权、数据库隔离 E4 | `OPEN` |
| `CAP-ORG-002` | Team 与 Owner | PRD 5.6、ADR-0002 | 无完整 Team 契约/表/API | 无 | `Q-103`/`Q-104`，Team 成员和 Owner 规则 | `OPEN` |
| `CAP-AUTH-001` | RBAC/Scope/ACL | PRD 5.5 | V4、authorization API | AuthorizationService、管理 API/页面、IT | 用例层统一授权；资源状态/敏感度；成功与防枚举 E4 | `PARTIAL` |
| `CAP-TAX-001` | 字典与 i18n | PRD 5.6 | V6、dictionary API | 字典后端和页面 | 真实 zh-CN/en-US 渲染、停用历史回显 E4、前端 i18n | `PARTIAL` |
| `CAP-TAX-002` | 受控标签 | PRD 5.6 | V7/V12、tag API | 标签后端和页面 | 组织作用域、资产历史回显、端到端自由标签拒绝 | `IMPLEMENTED_UNVERIFIED` |
| `CAP-CONF-001` | 类型化配置 | PRD 5.7 | V8、configuration API | 配置后端和页面 | 热更新行为、Secret 拒绝、版本冲突和审计 E4 | `IMPLEMENTED_UNVERIFIED` |
| `CAP-JOB-001` | 写接口幂等 | PRD 5.11 | V9、Idempotency header | Service/Store 存在，前端部分带 Header | Service 无业务调用方；请求摘要冲突和并发重放 E3/E4 | `PARTIAL` |
| `CAP-JOB-002` | 可靠任务 | PRD 5.11 | V9、job API | Worker、租约/退避代码和单测 | 业务 Handler、崩溃恢复、Dead 通知、并发领取 E4 | `PARTIAL` |
| `CAP-AUD-001` | 结构化日志与审计 | PRD 5.10 | V10、audit API | AuditService、Adapters、单测、登录审计 Verify | 必审计事件完整清单、拒绝/失败覆盖、JSON/MDC/脱敏 E4 | `PARTIAL` |
| `CAP-NOT-001` | 站内通知与签名 Webhook | PRD 5.12 | V11、event schema | Notification/Outbox/Webhook 代码和单测 | 核心事务原子性、失败后恢复、SSRF 与签名 E4 | `IMPLEMENTED_UNVERIFIED` |
| `CAP-OBS-001` | 指标、Trace、健康、诊断 | PRD 5.13、ADR-0002 | diagnostics API | Micrometer/诊断代码和单测 | OTel 范围冲突；REST/MCP/Worker Trace E4；Prometheus Target | `OPEN` |
| `CAP-UI-001` | 公共管理端 | PRD 5.14、15.7 | 页面路由/API | 多个管理页面可构建 | 无前端测试/浏览器 E2E；无运行时 i18n；受控值仍自由输入 | `PARTIAL` |
| `CAP-ASSET-001` | 资产目录安全整改 | PRD 6/8、ADR-0002 | V12、asset OpenAPI | 资产 CRUD/搜索、单测/IT、页面 | Team Owner、Gitea 一致性、授权全矩阵、完整治理与 E4 | `PARTIAL` |

## 3. 后续补全规则

每个能力必须继续拆成稳定的 `REQ-*` 与 `AC-*`。只有当：

- 每条 MUST 需求均为 `VERIFIED`；
- 契约、数据、后端、UI、测试和运行证据列均有可复现链接；
- 该能力的所有 `OPEN` 决策已经关闭；

能力状态才可以改为 `VERIFIED`。

